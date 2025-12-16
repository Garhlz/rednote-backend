package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.event.PostAuditEvent;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
        String token = jwtUtil.createToken(user.getId(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
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
            } else {
                vo.setPostCount(0L);
                vo.setLikeCount(0L);
                vo.setAvgScore(0.0);
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
        Query query = new Query();

        // 1. [MongoDB] 构建查询条件
        query.addCriteria(Criteria.where("isDeleted").is(0));
        query.addCriteria(Criteria.where("status").in(0, 2));

        if (StrUtil.isNotBlank(dto.getTitleKeyword())) {
            query.addCriteria(Criteria.where("title").regex(dto.getTitleKeyword(), "i"));
        }
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            query.addCriteria(Criteria.where("tags").all(dto.getTags()));
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            query.addCriteria(Criteria.where("createdAt")
                    .gte(dto.getStartTime().atStartOfDay())
                    .lte(dto.getEndTime().atTime(LocalTime.MAX)));
        }

        // 特殊处理：按邮箱筛选 (需要先查 PG 拿到 userId)
        if (StrUtil.isNotBlank(dto.getEmail())) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, dto.getEmail())
                    .select(User::getId)); // 只查 ID 优化性能
            if (user != null) {
                query.addCriteria(Criteria.where("userId").is(user.getId()));
            } else {
                return Map.of("records", new ArrayList<>(), "total", 0);
            }
        } else if (StrUtil.isNotBlank(dto.getNickname())) {
            // 按昵称模糊搜，Mongo 有冗余字段，直接查 Mongo
            query.addCriteria(Criteria.where("userNickname").regex(dto.getNickname(), "i"));
        }

        // 2. [MongoDB] 执行分页查询
        long total = mongoTemplate.count(query, PostDoc.class);
        Pageable pageable = PageRequest.of(dto.getPage() - 1, dto.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(pageable);
        List<PostDoc> docs = mongoTemplate.find(query, PostDoc.class);

        // 3. [PostgreSQL] 批量补全邮箱 (解决 N+1 问题)
        List<AdminPostVO> records;

        if (CollUtil.isEmpty(docs)) {
            records = new ArrayList<>();
        } else {
            // 3.1 提取所有作者 ID (去重)
            Set<Long> userIds = docs.stream()
                    .map(PostDoc::getUserId)
                    .collect(Collectors.toSet());

            // 3.2 批量查询 PG (SELECT id, email FROM users WHERE id IN (...))
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getId, userIds)
                    .select(User::getId, User::getEmail)); // 只查需要的字段

            // 3.3 转为 Map<UserId, Email> 方便查找
            Map<Long, String> emailMap = users.stream()
                    .collect(Collectors.toMap(User::getId, User::getEmail));

            // 3.4 内存组装
            records = docs.stream().map(doc -> {
                AdminPostVO vo = new AdminPostVO();
                BeanUtils.copyProperties(doc, vo);

                // 处理摘要 (防止内容过长)
                vo.setContent(StrUtil.subPre(doc.getContent(), 50));

                // 补全邮箱
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
        // 1. 先查询帖子 (我们需要知道作者是谁，才能发通知)
        PostDoc post = mongoTemplate.findById(postId, PostDoc.class);
        if (post == null) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 执行更新 (你的原有逻辑)
        Update update = new Update();
        update.set("status", status);
        // 如果是拒绝，建议把理由也存到帖子表里，方便作者修改时看到
        if (status == 2 && StrUtil.isNotBlank(reason)) {
            update.set("rejectReason", reason);
        }
        // 更新 updateAt
        update.set("updatedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(postId)), update, PostDoc.class);

        // 3. 【核心新增】发送异步事件到 RabbitMQ
        // 路由键建议定义为 "post.audit"
        PostAuditEvent event = new PostAuditEvent(
                post.getId(),
                post.getUserId(),
                post.getTitle(),
                status,
                reason
        );
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "post.audit", event);

        log.info("Admin audit post {}: status={}, reason={}, event sent.", postId, status, reason);
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

        // 1. 强制固定日志类型
        query.addCriteria(Criteria.where("logType").is(logType));

        // 2. 动态拼接条件
        if (dto.getUserId() != null) {
            query.addCriteria(Criteria.where("userId").is(dto.getUserId()));
        }

        if (dto.getStatus() != null) {
            query.addCriteria(Criteria.where("status").is(dto.getStatus()));
        }

        // 精确查询业务ID (例如查询某个帖子的所有操作历史)
        if (StrUtil.isNotBlank(dto.getBizId())) {
            query.addCriteria(Criteria.where("bizId").is(dto.getBizId()));
        }

        // 模糊查询：同时匹配 "中文描述" 或 "请求路径"
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("description").regex(dto.getKeyword(), "i"), // i 表示忽略大小写
                    Criteria.where("uri").regex(dto.getKeyword(), "i")
            ));
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
}
