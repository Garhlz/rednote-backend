package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.event.PostAuditEvent;
import com.szu.afternoon3.platform.event.PostAuditPassEvent;
import com.szu.afternoon3.platform.event.UserDeleteEvent;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.UserFollowRepository;
import com.szu.afternoon3.platform.service.AdminService;
import com.szu.afternoon3.platform.util.JwtUtil;
import com.szu.afternoon3.platform.util.TencentImUtil;
import com.szu.afternoon3.platform.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminServiceImpl implements AdminService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private TencentImUtil tencentImUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserFollowRepository userFollowRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate; // 注入

    @Override
    public LoginVO login(String account, String password) {
        // 1. 查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account));

        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 校验密码
        if (user.getPassword() == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new AppException(ResultCode.ACCOUNT_PASSWORD_ERROR);
        }

        // 3. 校验权限
        if (!"ADMIN".equals(user.getRole())) {
            throw new AppException(ResultCode.PERMISSION_DENIED, "非管理员账号无法登录后台");
        }

        // 4. 账号状态
        if (user.getStatus() == 0) {
            throw new AppException(ResultCode.ACCOUNT_BANNED);
        }

        return buildLoginVO(user);
    }

    private LoginVO buildLoginVO(User user) {
        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getRole(), user.getNickname());
        String refreshToken =  jwtUtil.createRefreshToken(user.getId());
        LoginVO vo = new LoginVO();
        vo.setToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setHasPassword(true);
        vo.setIsNewUser(false);

        UserInfo info = new UserInfo();
        info.setUserId(user.getId().toString());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());
        vo.setUserInfo(info);

        String userSig = tencentImUtil.genUserSig(user.getId().toString());
        vo.setUserSig(userSig);
        return vo;
    }

    @Override
    public UserInfo getAdminInfo() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }

        UserInfo info = new UserInfo();
        info.setUserId(user.getId().toString());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setEmail(user.getEmail());
        // 补充注册时间等，UserInfo暂无，如果需要可以扩展或新建VO
        // 需求说：显示账户的默认头像、账户用户名、用户组身份及该管理员账号注册时间
        // UserInfo 只有基本信息。可能需要扩展 UserInfo 或者返回 UserProfileVO
        // 这里暂时返回 UserInfo，前端如果不够用，可以改

        return info;
    }

    @Override
    public Map<String, Object> getUserList(AdminUserSearchDTO dto) {
        // 1. [PostgreSQL] 查询用户基础分页数据
        Page<User> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(dto.getNickname())) {
            wrapper.like(User::getNickname, dto.getNickname());
        }
        if (StrUtil.isNotBlank(dto.getEmail())) {
            wrapper.like(User::getEmail, dto.getEmail());
        }
        if (dto.getStartTime() != null) {
            wrapper.ge(User::getCreatedAt, dto.getStartTime().atStartOfDay());
        }
        if (dto.getEndTime() != null) {
            wrapper.le(User::getCreatedAt, dto.getEndTime().atTime(LocalTime.MAX));
        }

        IPage<User> userPage = userMapper.selectPage(page, wrapper);
        List<User> userList = userPage.getRecords();

        // 快速返回：如果没有用户，直接返回空，避免后续 Mongo 报错
        if (userList.isEmpty()) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("records", new ArrayList<>());
            emptyResult.put("total", 0L);
            return emptyResult;
        }

        // 2. 提取本页所有用户的 ID 列表
        List<Long> userIds = userList.stream().map(User::getId).collect(Collectors.toList());

        // 3. [MongoDB] 批量聚合查询统计数据 (使用 $in 操作符)

        // 3.1 批量查询：帖子数、获赞数、平均分 (数据源: posts)
        // Group by userId, Count(id), Sum(likeCount), Avg(ratingAverage)
        Aggregation postAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").in(userIds).and("isDeleted").is(0)),
                Aggregation.group("userId")
                        .count().as("postCount")
                        .sum("likeCount").as("totalLikes")
                        .avg("ratingAverage").as("avgScore")
                        .sum(ConditionalOperators.when(
                                ComparisonOperators.valueOf("status").equalToValue(0)
                        ).then(1).otherwise(0)).as("pendingPostCount")
                        .sum(ConditionalOperators.when(
                                ComparisonOperators.valueOf("status").equalToValue(1)
                        ).then(1).otherwise(0)).as("passedPostCount")
                        .sum(ConditionalOperators.when(
                                ComparisonOperators.valueOf("status").equalToValue(2)
                        ).then(1).otherwise(0)).as("rejectedPostCount")
        );
        // 结果转 Map: userId -> 统计对象
        Map<Long, Map> postStatsMap = mongoTemplate.aggregate(postAgg, PostDoc.class, Map.class)
                .getMappedResults().stream()
                .collect(Collectors.toMap(
                        item -> ((Number) item.get("_id")).longValue(), // Key: userId
                        item -> item // Value: 整个统计结果
                ));

        // 3.2 批量查询：粉丝数 (数据源: user_follows, targetUserId IN userIds)
        Aggregation fanAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("targetUserId").in(userIds)),
                Aggregation.group("targetUserId").count().as("count")
        );
        Map<Long, Long> fanCountMap = mongoTemplate.aggregate(fanAgg, UserFollowDoc.class, Map.class)
                .getMappedResults().stream()
                .collect(Collectors.toMap(
                        item -> ((Number) item.get("_id")).longValue(),
                        item -> ((Number) item.get("count")).longValue()
                ));

        // 3.3 批量查询：关注数 (数据源: user_follows, userId IN userIds)
        Aggregation followAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").in(userIds)),
                Aggregation.group("userId").count().as("count")
        );
        Map<Long, Long> followCountMap = mongoTemplate.aggregate(followAgg, UserFollowDoc.class, Map.class)
                .getMappedResults().stream()
                .collect(Collectors.toMap(
                        item -> ((Number) item.get("_id")).longValue(),
                        item -> ((Number) item.get("count")).longValue()
                ));

        // 4. [内存] 组装最终 VO
        List<AdminUserVO> records = new ArrayList<>();
        for (User user : userList) {
            AdminUserVO vo = new AdminUserVO();
            BeanUtils.copyProperties(user, vo);
            vo.setRegisterTime(user.getCreatedAt());

            Long uid = user.getId();

            // 填充帖子相关数据 (从 Map 获取，避免空指针)
            Map stats = postStatsMap.get(uid);
            if (stats != null) {
                vo.setPostCount(stats.get("postCount") != null ? ((Number) stats.get("postCount")).longValue() : 0L);
                vo.setLikeCount(stats.get("totalLikes") != null ? ((Number) stats.get("totalLikes")).longValue() : 0L);
                vo.setAvgScore(stats.get("avgScore") != null ? ((Number) stats.get("avgScore")).doubleValue() : 0.0);
                vo.setPendingPostCount(parseToLong(stats.get("pendingPostCount")));
                vo.setPassedPostCount(parseToLong(stats.get("passedPostCount")));
                vo.setRejectedPostCount(parseToLong(stats.get("rejectedPostCount")));
            } else {
                vo.setPostCount(0L);
                vo.setLikeCount(0L);
                vo.setAvgScore(0.0);
                vo.setPendingPostCount(0L);
                vo.setPassedPostCount(0L);
                vo.setRejectedPostCount(0L);
            }

            // 填充粉丝/关注
            vo.setFanCount(fanCountMap.getOrDefault(uid, 0L));
            vo.setFollowCount(followCountMap.getOrDefault(uid, 0L));

            records.add(vo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", userPage.getTotal());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, String reason) {
        // 1. 检查用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        // 2. [PostgreSQL] 物理删除用户 (或逻辑删除，取决于你的 global-config)
        // MyBatis-Plus 会根据你的配置自动处理逻辑删除
        userMapper.deleteById(userId);

        // 3. 【RabbitMQ】发送用户删除事件
        // 路由键: user.delete (与 UserServiceImpl 中的更新事件区分开)
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "user.delete",
                new UserDeleteEvent(userId));

        log.info("管理员删除用户成功: userId={}, reason={}", userId, reason);
    }

    @Override
    public Map<String, Object> getPostList(AdminPostSearchDTO dto) {
        // 1. 构建基础过滤条件 (Match)
        Criteria criteria = new Criteria();
        List<Criteria> andCriteria = new ArrayList<>();

        // === Tab 过滤逻辑 ===
        int tab = dto.getTab() == null ? 0 : dto.getTab();
        switch (tab) {
            case 1: // 待审核
                andCriteria.add(Criteria.where("status").is(0));
                andCriteria.add(Criteria.where("isDeleted").is(0));
                break;
            case 2: // 已通过
                andCriteria.add(Criteria.where("status").is(1));
                andCriteria.add(Criteria.where("isDeleted").is(0));
                break;
            case 3: // 已拒绝
                andCriteria.add(Criteria.where("status").is(2));
                andCriteria.add(Criteria.where("isDeleted").is(0));
                break;
            case 4: // 回收站
                andCriteria.add(Criteria.where("isDeleted").is(1));
                break;
            default: // 0 = 全部
                // 全部模式下，不限制 status 和 isDeleted，
                // 但为了不把彻底物理删除的数据查出来(如果你的业务有)，通常不需额外条件
                // 这里我们要查出所有逻辑删除(isDeleted=1)和正常帖子
                break;
        }

        // === 通用条件过滤 ===
        if (StrUtil.isNotBlank(dto.getTitleKeyword())) {
            andCriteria.add(Criteria.where("title").regex(dto.getTitleKeyword(), "i"));
        }
        if (CollUtil.isNotEmpty(dto.getTags())) {
            andCriteria.add(Criteria.where("tags").all(dto.getTags()));
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            andCriteria.add(Criteria.where("createdAt")
                    .gte(dto.getStartTime().atStartOfDay())
                    .lte(dto.getEndTime().atTime(LocalTime.MAX)));
        }

        // === 邮箱/昵称处理 ===
        if (StrUtil.isNotBlank(dto.getEmail())) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, dto.getEmail())
                    .select(User::getId));
            if (user != null) {
                andCriteria.add(Criteria.where("userId").is(user.getId()));
            } else {
                return Map.of("records", new ArrayList<>(), "total", 0L);
            }
        } else if (StrUtil.isNotBlank(dto.getNickname())) {
            andCriteria.add(Criteria.where("userNickname").regex(dto.getNickname(), "i"));
        }

        if (!andCriteria.isEmpty()) {
            criteria.andOperator(andCriteria.toArray(new Criteria[0]));
        }

        // 2. 计算总数 (Count)
        // 聚合分页前必须先查总数，这里直接用 Query 查 count 比较简单
        long total = mongoTemplate.count(Query.query(criteria), PostDoc.class);
        if (total == 0) {
            return Map.of("records", new ArrayList<>(), "total", 0L);
        }

        // 3. 构建聚合管道 (Aggregation Pipeline)
        List<AggregationOperation> operations = new ArrayList<>();

        // 3.1 Match (应用过滤条件)
        operations.add(Aggregation.match(criteria));

        // 3.2 AddFields (核心：计算自定义排序权重)
        // 仅在 Tab=0 (全部) 时需要这个复杂排序：未审核(0) -> 拒绝(2) -> 删除(isDeleted=1) -> 通过(1)
        if (tab == 0) {
            ConditionalOperators.Switch switchOp = ConditionalOperators.switchCases(
                    // Case 1: isDeleted == 1 -> 权重 30
                    ConditionalOperators.Switch.CaseOperator.when(
                            ComparisonOperators.valueOf("isDeleted").equalToValue(1)
                    ).then(30),

                    // Case 2: status == 0 -> 权重 10
                    ConditionalOperators.Switch.CaseOperator.when(
                            ComparisonOperators.valueOf("status").equalToValue(0)
                    ).then(10),

                    // Case 3: status == 2 -> 权重 20
                    ConditionalOperators.Switch.CaseOperator.when(
                            ComparisonOperators.valueOf("status").equalToValue(2)
                    ).then(20)
            ).defaultTo(40); // 默认: 已通过 (status=1) -> 权重 40

            operations.add(Aggregation.addFields().addField("sortWeight").withValue(switchOp).build());

            // 排序：先按权重 ASC (10->20->30->40)，再按时间 DESC
            operations.add(Aggregation.sort(Sort.by(Sort.Direction.ASC, "sortWeight").and(Sort.by(Sort.Direction.DESC, "createdAt"))));
        } else {
            // 其他 Tab 只需要按时间倒序
            operations.add(Aggregation.sort(Sort.Direction.DESC, "createdAt"));
        }

        // 3.3 Skip & Limit (分页)
        operations.add(Aggregation.skip((long) (dto.getPage() - 1) * dto.getSize()));
        operations.add(Aggregation.limit(dto.getSize()));

        // 4. 执行聚合查询
        Aggregation aggregation = Aggregation.newAggregation(operations);
        List<PostDoc> docs = mongoTemplate.aggregate(aggregation, "posts", PostDoc.class).getMappedResults();

        // 5. [PostgreSQL] 批量补全邮箱 (保持原逻辑不变)
        List<AdminPostVO> records = new ArrayList<>();
        if (CollUtil.isNotEmpty(docs)) {
            Set<Long> userIds = docs.stream().map(PostDoc::getUserId).collect(Collectors.toSet());
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getId, userIds)
                    .select(User::getId, User::getEmail));
            Map<Long, String> emailMap = users.stream().collect(Collectors.toMap(User::getId, User::getEmail));

            records = docs.stream().map(doc -> {
                AdminPostVO vo = new AdminPostVO();
                BeanUtils.copyProperties(doc, vo);
                vo.setContent(StrUtil.subPre(doc.getContent(), 50));
                vo.setUserEmail(emailMap.get(doc.getUserId()));
                return vo;
            }).collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        return result;
    }

    private AdminPostVO convertToAdminPostVO(PostDoc doc) {
        AdminPostVO vo = new AdminPostVO();
        BeanUtils.copyProperties(doc, vo);

        // 补全用户信息 (PostDoc里有)
        vo.setUserNickname(doc.getUserNickname());
        vo.setUserAvatar(doc.getUserAvatar());

        // 补全邮箱 (PostDoc里没有) - 需要查 User 表
        // 优化：如果有缓存最好。这里逐个查可能慢。
        // 但考虑到后台管理并发低，先这样。
        User user = userMapper.selectById(doc.getUserId());
        if (user != null) {
            vo.setUserEmail(user.getEmail());
        }

        return vo;
    }

    @Override
    public PostVO getPostDetail(String postId) {
        PostDoc doc = mongoTemplate.findById(postId, PostDoc.class);
        if (doc == null) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        PostVO vo = new PostVO();
        BeanUtils.copyProperties(doc, vo);

        // 处理资源
        if (doc.getType() == 1) { // Video
            if (doc.getResources() != null && !doc.getResources().isEmpty()) {
                vo.setVideo(doc.getResources().get(0));
            }
        } else { // Image
            vo.setImages(doc.getResources());
        }

        // 处理作者信息
        UserInfo author = new UserInfo();
        author.setUserId(String.valueOf(doc.getUserId()));
        author.setNickname(doc.getUserNickname());
        author.setAvatar(doc.getUserAvatar());

        User user = userMapper.selectById(doc.getUserId());
        if (user != null) {
            author.setEmail(user.getEmail());
        }
        vo.setAuthor(author);

        // 格式化时间
        // PostVO uses String createdAt
        // PostDoc uses LocalDateTime createdAt
        // simple formatting
        // TODO 修改时间
        vo.setCreatedAt(doc.getCreatedAt().toString());

        return vo;
    }

    @Override
    public void auditPost(String postId, Integer status, String reason) {
        // 1. 先查询帖子
        PostDoc post = mongoTemplate.findById(postId, PostDoc.class);
        if (post == null) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 获取当前操作管理员的信息 【新增】
        Long adminId = UserContext.getUserId();
        User admin = userMapper.selectById(adminId); // 获取管理员昵称
        String adminName = (admin != null) ? admin.getNickname() : "Unknown Admin";

        // 3. 执行更新 (原有逻辑)
        Update update = new Update();
        update.set("status", status);
        if (status == 2 && StrUtil.isNotBlank(reason)) {
            update.set("rejectReason", reason);
        }
        update.set("updatedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(postId)), update, PostDoc.class);

        // 4. 【修改】发送异步事件 (填入操作人信息)
        PostAuditEvent event = new PostAuditEvent(
                post.getId(),
                post.getUserId(),
                post.getTitle(),
                status,
                reason,
                adminId,    // set operatorId
                adminName   // set operatorName
        );
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "post.audit", event);


        if (status == 1) {
            PostAuditPassEvent passEvent = new PostAuditPassEvent();
            BeanUtils.copyProperties(post,passEvent);

            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "post.audit.pass", passEvent);

        }
        log.info("管理员审核帖子 {}: status={}, reason={}, operator={}", postId, status, reason, adminName);
    }

    @Override
    public AdminUserDetailVO getUserDetail(Long userId) {
        // 1. [PostgreSQL] 查询基础用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }

        AdminUserDetailVO vo = new AdminUserDetailVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRegisterTime(user.getCreatedAt());

        // =====================================================
        // 2. [MongoDB] 查询主动行为 (Simple Count Queries)
        // =====================================================

        // 2.1 发帖数 (注意过滤已删除的)
        long postCount = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId).and("isDeleted").is(0)),
                PostDoc.class
        );
        vo.setPostCount(postCount);

        // 2.2 关注数 (我关注了谁)
        long followCount = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId)),
                UserFollowDoc.class
        );
        vo.setFollowCount(followCount);

        // 2.3 发出的点赞 (查询 post_likes 表)
        long givenLike = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId)),
                PostLikeDoc.class
        );
        vo.setGivenLikeCount(givenLike);

        // 2.4 发出的收藏 (查询 post_collects 表)
        long givenCollect = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId)),
                PostCollectDoc.class
        );
        vo.setGivenCollectCount(givenCollect);

        // 2.5 发出的评论 (查询 comments 表) - [新增实现]
        long givenComment = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId)),
                CommentDoc.class
        );
        vo.setGivenCommentCount(givenComment);

        // 2.6 发出的评分 (查询 post_ratings 表) - [新增实现]
        long givenRate = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId)),
                PostRatingDoc.class
        );
        vo.setGivenRateCount(givenRate);


        // =====================================================
        // 3. [MongoDB] 查询被动影响力 (Aggregation)
        // =====================================================

        // 3.1 粉丝数 (谁关注了我) - 直接查关联表
        long fanCount = mongoTemplate.count(
                Query.query(Criteria.where("targetUserId").is(userId)),
                UserFollowDoc.class
        );
        vo.setFanCount(fanCount);

        // 3.2 聚合统计：获得的赞、收藏、评论、平均分
        // 原理：在 posts 表中找到该用户所有未删除的帖子，对各项计数器求和(Sum)或求平均(Avg)
        Aggregation agg = Aggregation.newAggregation(
                // 步骤A: 筛选该用户的有效帖子
                Aggregation.match(Criteria.where("userId").is(userId).and("isDeleted").is(0)),

                // 步骤B: 全局分组计算
                Aggregation.group()
                        .sum("likeCount").as("totalLikes")       // 累加获赞
                        .sum("collectCount").as("totalCollects") // 累加被收藏
                        .sum("commentCount").as("totalComments") // 累加被评论 [新增]
                        .avg("ratingAverage").as("avgScore")     // 计算平均分
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, PostDoc.class, Map.class);
        Map<String, Object> map = results.getUniqueMappedResult();

        if (map != null) {
            // 注意：Mongo聚合返回的数字可能是 Integer, Long 或 Double，建议转 Number 处理
            vo.setReceivedLikeCount(parseToLong(map.get("totalLikes")));
            vo.setReceivedCollectCount(parseToLong(map.get("totalCollects")));
            vo.setReceivedCommentCount(parseToLong(map.get("totalComments"))); // [新增]
            vo.setAvgPostScore(parseToDouble(map.get("avgScore")));
        } else {
            // 如果该用户没发过帖子，各项数据为 0
            vo.setReceivedLikeCount(0L);
            vo.setReceivedCollectCount(0L);
            vo.setReceivedCommentCount(0L);
            vo.setAvgPostScore(0.0);
        }

        return vo;
    }

    // 辅助方法：处理 Mongo 返回的数字类型转换安全问题
    private Long parseToLong(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }

    private Double parseToDouble(Object obj) {
        if (obj instanceof Number) {
            // 保留一位小数 (可选)
            double val = ((Number) obj).doubleValue();
            return (double) Math.round(val * 10) / 10;
        }
        return 0.0;
    }
    @Value("${szu.oss.default-avatar}") private String defaultAvatar;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTestUser(TestUserCreateDTO dto) {
        // 1. 查重
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));
        if (count > 0) {
            throw new AppException(ResultCode.EMAIL_ALREADY_EXISTS, "该测试邮箱已存在");
        }

        // 2. 插入用户
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setNickname(dto.getNickname());
        // 务必加密密码
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setAvatar(defaultAvatar);
        user.setRole("ADMIN");
        user.setStatus(1);

        // 为了测试方便，随便给个 openid，防止唯一索引冲突
        user.setOpenid("test_openid_" + System.currentTimeMillis());

        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public Map<String, Object> getAdminLogs(LogSearchDTO dto) {
        return queryLogs("ADMIN_OPER", dto);
    }

    @Override
    public Map<String, Object> getUserLogs(LogSearchDTO dto) {
        return queryLogs("USER_OPER", dto);
    }

    /**
     * 通用日志查询私有方法
     * @param logType 日志类型 (ADMIN_OPER / USER_OPER)
     * @param dto 查询条件
     */
    private Map<String, Object> queryLogs(String logType, LogSearchDTO dto) {
        Query query = new Query();

        // 1. 强制固定日志类型 (Admin/User)
        query.addCriteria(Criteria.where("logType").is(logType));

        // 2. 精确匹配条件
        if (dto.getUserId() != null) {
            query.addCriteria(Criteria.where("userId").is(dto.getUserId()));
        }
        if (dto.getStatus() != null) {
            query.addCriteria(Criteria.where("status").is(dto.getStatus()));
        }
        if (StrUtil.isNotBlank(dto.getBizId())) {
            query.addCriteria(Criteria.where("bizId").is(dto.getBizId()));
        }

        // 【新增】操作类型(模块)精确筛选
        if (StrUtil.isNotBlank(dto.getModule())) {
            query.addCriteria(Criteria.where("module").is(dto.getModule()));
        }

        // 3. 模糊查询条件 (使用 orOperator 组合)
        List<Criteria> orCriteria = new ArrayList<>();

        // 关键词搜 描述 或 路径
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            orCriteria.add(Criteria.where("description").regex(dto.getKeyword(), "i"));
            orCriteria.add(Criteria.where("uri").regex(dto.getKeyword(), "i"));
        }

        // 【新增】操作人昵称模糊搜
        if (StrUtil.isNotBlank(dto.getUsername())) {
            orCriteria.add(Criteria.where("username").regex(dto.getUsername(), "i"));
        }

        // 如果有模糊条件，加入查询
        if (!orCriteria.isEmpty()) {
            query.addCriteria(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
        }

        // 时间范围查询
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            query.addCriteria(Criteria.where("createdAt")
                    .gte(dto.getStartTime())
                    .lte(dto.getEndTime()));
        }

        // 3. 计算总数 (用于分页)
        long total = mongoTemplate.count(query, ApiLogDoc.class);

        // 4. 构建分页与排序 (按创建时间倒序)
        int page = dto.getPage() > 0 ? dto.getPage() - 1 : 0;
        Pageable pageable = PageRequest.of(page, dto.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(pageable);

        // 5. 执行查询
        List<ApiLogDoc> list = mongoTemplate.find(query, ApiLogDoc.class);

        // 6. 组装结果
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("records", list);
        result.put("current", dto.getPage());
        result.put("size", dto.getSize());

        return result;
    }

    /**
     * 统计热门标签 (基于浏览量总和排序)
     */
    public List<TagStatVO> getHotTagStats() {
        // 聚合管道
        // 1. Unwind: 把 tags 数组拆开 (一帖多标 -> 多条记录)
        // 2. Group: 按 tag 分组，sum(viewCount), count(id)
        // 3. Sort: 按浏览量倒序
        // 4. Limit: 前 20 个

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("isDeleted").is(0).and("status").is(1)), // 只算有效帖子
                Aggregation.unwind("tags"),
                Aggregation.group("tags")
                        .sum("viewCount").as("totalViews")
                        .count().as("postCount"),
                Aggregation.sort(Sort.Direction.DESC, "totalViews"),
                Aggregation.limit(20),
                Aggregation.project("totalViews", "postCount").and("_id").as("tagName") // 映射字段
        );

        AggregationResults<TagStatVO> results = mongoTemplate.aggregate(agg, "posts", TagStatVO.class);
        return results.getMappedResults();
    }

    /**
     * 获取帖子浏览量排行
     * @param limit 前多少名 (比如 Top 50)
     */
    @Override
    public List<AdminPostStatVO> getPostViewRanking(int limit) {
        Query query = new Query();

        // 1. 筛选条件：只看有效帖子
        query.addCriteria(Criteria.where("isDeleted").is(0));
        query.addCriteria(Criteria.where("status").is(1)); // 只看已发布的

        // 2. 核心：按 viewCount 倒序 (DESC)
        query.with(Sort.by(Sort.Direction.DESC, "viewCount"));

        // 3. 限制条数
        query.limit(limit);

        // 4. 查询
        List<PostDoc> docs = mongoTemplate.find(query, PostDoc.class);

        // 5. 转换为 VO
        return docs.stream().map(doc -> {
            AdminPostStatVO vo = new AdminPostStatVO();
            vo.setId(doc.getId());
            vo.setTitle(doc.getTitle());
            vo.setCover(doc.getCover());

            // 重点数据
            vo.setViewCount(doc.getViewCount());
            vo.setLikeCount(doc.getLikeCount());
            vo.setCommentCount(doc.getCommentCount());
            vo.setCollectCount(doc.getCollectCount());
            vo.setRatingAverage(doc.getRatingAverage());

            vo.setUserNickname(doc.getUserNickname());
            // 时间格式化
            if (doc.getCreatedAt() != null) {
                vo.setCreatedAt(doc.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<PostAuditLogVO> getPostAuditHistory(String postId) {
        // 1. 构建查询
        Query query = new Query();
        query.addCriteria(Criteria.where("postId").is(postId));

        // 2. 按时间倒序
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        // 3. 执行查询
        List<PostAuditLogDoc> logs = mongoTemplate.find(query, PostAuditLogDoc.class);

        // 4. 转换 VO
        return logs.stream().map(doc -> {
            PostAuditLogVO vo = new PostAuditLogVO();
            BeanUtils.copyProperties(doc, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public AdminStatsVO getDataStatistics() {
        AdminStatsVO vo = new AdminStatsVO();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDaysAgoStart = LocalDate.now().minusDays(6).atStartOfDay(); // 近7天包含今天

        // ==========================================
        // 1. 顶部卡片：今日数据
        // ==========================================

        // 1.1 今日新增用户 (PG)
        Long todayUserCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, todayStart));
        vo.setTodayNewUsers(todayUserCount);

        // 1.2 今日发帖量 (Mongo)
        long todayPostCount = mongoTemplate.count(Query.query(
                Criteria.where("createdAt").gte(todayStart)), PostDoc.class);
        vo.setTodayNewPosts(todayPostCount);


        // ==========================================
        // 2. 图表数据：近7天趋势
        // ==========================================
        // 准备最近 7 天的日期列表 (作为 X 轴标准)
        List<String> last7Days = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            last7Days.add(LocalDate.now().minusDays(i).format(fmt));
        }

        // 2.1 用户增长趋势 (PostgreSQL)
        // SQL: SELECT TO_CHAR(created_at, 'YYYY-MM-DD') as date, COUNT(*) as count
        //      FROM users WHERE created_at >= ? GROUP BY date
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.select("TO_CHAR(created_at, 'yyyy-MM-dd') as date", "COUNT(*) as count")
                .ge("created_at", sevenDaysAgoStart)
                .groupBy("TO_CHAR(created_at, 'yyyy-MM-dd')");

        List<Map<String, Object>> userTrendRaw = userMapper.selectMaps(userQuery);
        // 转 Map 方便查找: {"2023-12-01": 10, ...}
        Map<String, Long> userTrendMap = userTrendRaw.stream().collect(Collectors.toMap(
                m -> (String) m.get("date"),
                m -> ((Number) m.get("count")).longValue()
        ));

        // 2.2 发帖趋势 (MongoDB Aggregation)
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(sevenDaysAgoStart)),
                Aggregation.project().and(DateOperators.dateOf("createdAt").toString("%Y-%m-%d")).as("date"),
                Aggregation.group("date").count().as("count")
        );
        List<Map> postTrendRaw = mongoTemplate.aggregate(agg, "posts", Map.class).getMappedResults();
        Map<String, Long> postTrendMap = postTrendRaw.stream().collect(Collectors.toMap(
                m -> (String) m.get("_id"), // group 的 key 是 _id
                m -> ((Number) m.get("count")).longValue()
        ));

        // 2.3 组装并补 全 0 值
        vo.setUserTrend(buildChartData(last7Days, userTrendMap));
        vo.setPostTrend(buildChartData(last7Days, postTrendMap));


        // ==========================================
        // 3. 饼图：用户地区分布 (PostgreSQL)
        // ==========================================
        // SQL: SELECT region, COUNT(*) as count FROM users GROUP BY region
        QueryWrapper<User> regionQuery = new QueryWrapper<>();
        regionQuery.select("region", "COUNT(*) as count")
                .groupBy("region");
        List<Map<String, Object>> regionRaw = userMapper.selectMaps(regionQuery);

        List<AdminStatsVO.NameValueVO> regionStats = regionRaw.stream()
                .map(m -> {
                    String region = (String) m.get("region");
                    // 处理空地区
                    if (StrUtil.isBlank(region)) region = "未知";
                    Long count = ((Number) m.get("count")).longValue();
                    return new AdminStatsVO.NameValueVO(region, count);
                })
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue())) // 按人数降序
                .collect(Collectors.toList());

        vo.setRegionStats(regionStats);

        return vo;
    }

    /**
     * 辅助方法：根据日期基准列表，填充数据，补 0
     */
    private AdminStatsVO.ChartDataVO buildChartData(List<String> dates, Map<String, Long> dataMap) {
        AdminStatsVO.ChartDataVO chartData = new AdminStatsVO.ChartDataVO();
        chartData.setDates(dates);

        List<Long> values = new ArrayList<>();
        for (String date : dates) {
            values.add(dataMap.getOrDefault(date, 0L));
        }
        chartData.setValues(values);
        return chartData;
    }

    @Override
    public void exportLogs(LogSearchDTO dto, HttpServletResponse response) {
        // 1. 构建查询条件 (逻辑复用 queryLogs，但这里为了独立性重写一遍，且不分页)
        Query query = new Query();

        // 强制只导出管理员日志 (通常需求如此，也可根据 dto 动态定)
        query.addCriteria(Criteria.where("logType").is("ADMIN_OPER"));

        if (dto.getUserId() != null) {
            query.addCriteria(Criteria.where("userId").is(dto.getUserId()));
        }
        if (dto.getStatus() != null) {
            query.addCriteria(Criteria.where("status").is(dto.getStatus()));
        }
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("description").regex(dto.getKeyword(), "i"),
                    Criteria.where("uri").regex(dto.getKeyword(), "i")
            ));
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            query.addCriteria(Criteria.where("createdAt")
                    .gte(dto.getStartTime())
                    .lte(dto.getEndTime()));
        }

        // 2. 排序与限制 (防止导出数据量过大导致 OOM，限制 5000 条)
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.limit(5000);

        // 3. 查询数据
        List<ApiLogDoc> logs = mongoTemplate.find(query, ApiLogDoc.class);

        // 4. 转换为 Map 列表 (保证 Excel 列顺序)
        List<Map<String, Object>> rows = logs.stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("时间", log.getCreatedAt().toString());
            row.put("操作人ID", log.getUserId());
            row.put("角色", log.getRole());
            row.put("模块", log.getModule());
            row.put("操作内容", log.getDescription());
            row.put("业务ID", log.getBizId()); // 关键业务对象ID
            row.put("请求路径", log.getUri());
            row.put("请求方法", log.getMethod());
            row.put("IP地址", log.getIp());
            row.put("耗时(ms)", log.getTimeCost());
            row.put("状态", log.getStatus() == 200 ? "成功" : "失败");
            row.put("错误信息", log.getErrorMsg());
            return row;
        }).collect(Collectors.toList());

        // 5. 使用 Hutool 写入响应流
        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            // 设置列宽自适应
            writer.autoSizeColumnAll();
            // 写入数据
            writer.write(rows, true);

            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
            String fileName = URLEncoder.encode("操作日志_" + System.currentTimeMillis() + ".xlsx", "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            //由于是在 Controller 中直接响应流，这里需要 flush 到底层
            writer.flush(response.getOutputStream(), true);
        } catch (IOException e) {
            log.error("导出 Excel 失败", e);
            throw new AppException(ResultCode.SYSTEM_ERROR, "导出文件失败");
        }
    }
}
