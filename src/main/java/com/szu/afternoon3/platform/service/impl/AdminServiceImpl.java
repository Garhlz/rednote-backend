package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.PostCollectDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.PostLikeDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import com.szu.afternoon3.platform.entity.mongo.PostRatingDoc;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.event.UserDeleteEvent;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.UserFollowRepository;
import com.szu.afternoon3.platform.service.AdminService;
import com.szu.afternoon3.platform.util.JwtUtil;
import com.szu.afternoon3.platform.util.TencentImUtil;
import com.szu.afternoon3.platform.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    private ApplicationEventPublisher eventPublisher;
    @Override
    public LoginVO login(String account, String password) {
        // 1. 查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account)
                .or()
                .eq(User::getNickname, account));

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

        // 3. [Event] 发布用户删除事件，异步清理 MongoDB 数据
        // source 传 this，userId 传目标用户ID
        eventPublisher.publishEvent(new UserDeleteEvent(this, userId));

        log.info("管理员删除用户成功: userId={}, reason={}", userId, reason);
    }
    @Override
    public Map<String, Object> getPostList(AdminPostSearchDTO dto) {
        Query query = new Query();

        // 1. [MongoDB] 构建查询条件
        query.addCriteria(Criteria.where("isDeleted").is(0));

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

                // Mongo 里的冗余信息
                vo.setUserNickname(doc.getUserNickname());
                vo.setUserAvatar(doc.getUserAvatar());

                // 补全邮箱 (从 Map 取)
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
        vo.setCreatedAt(doc.getCreatedAt().toString());

        return vo;
    }

    @Override
    public void auditPost(String postId, Integer status, String reason) {
        // status: 1=通过, 2=不通过(撤回)
        Update update = new Update();
        update.set("status", status);
        // 可以记录 reason

        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(postId)), update, PostDoc.class);

        log.info("Admin audit post {}: status={}, reason={}", postId, status, reason);
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
}
