package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.event.InteractionEvent;
import com.szu.afternoon3.platform.event.UserUpdateEvent;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.*;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.UserInfo;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import com.szu.afternoon3.platform.vo.UserSearchVO;
import com.szu.afternoon3.platform.vo.PostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserFollowRepository userFollowRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public UserProfileVO getUserProfile() {
        User user = getCurrentUser();
        return buildProfileVO(user);
    }

    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostCollectRepository postCollectRepository;
    @Autowired
    private PostRatingRepository postRatingRepository;
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostViewHistoryRepository postViewHistoryRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UserProfileUpdateDTO dto) {
        User user = getCurrentUser();

        boolean changed = false;
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
            changed = true;
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
            changed = true;
        }
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
            changed = true;
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
            changed = true;
        }
        if (dto.getRegion() != null) {
            user.setRegion(dto.getRegion());
            changed = true;
        }
        if (dto.getBirthday() != null) {
            try {
                user.setBirthday(LocalDate.parse(dto.getBirthday(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                changed = true;
            } catch (Exception e) {
                throw new AppException(ResultCode.PARAM_ERROR); // 日期格式错误
            }
        }

        // 发送 RabbitMQ 消息 (UserUpdateEvent)
        if (dto.getNickname() != null || dto.getAvatar() != null) {
            UserUpdateEvent event = new UserUpdateEvent(
                    user.getId(),
                    user.getNickname(),
                    user.getAvatar()
            );
            // 路由键: user.update
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "user.update", event);
        }
        if (changed) {
            userMapper.updateById(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> bindEmail(UserBindEmailDTO dto) {
        // 1. 校验验证码 (Mock)
        verifyCode(dto.getEmail(), dto.getCode());

        // 2. 检查邮箱是否已被占用
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, dto.getEmail()));
        if (count > 0) {
            throw new AppException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 3. 更新用户邮箱
        User user = getCurrentUser();
        user.setEmail(dto.getEmail());
        userMapper.updateById(user);

        Map<String, String> result = new HashMap<>();
        result.put("email", user.getEmail());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPasswordWithCode(UserPasswordSetDTO dto) {
        User user = getCurrentUser();

        // 1. 必须先绑定邮箱才能通过验证码设置密码 (逻辑上)
        // 或者这里的 code 是发给邮箱的，意味着 dto 应该包含 email?
        // Apifox 定义只有 code 和 password，说明 code 是关联到当前用户的邮箱的。
        // 所以前提是用户已经绑定了邮箱。
        if (StrUtil.isBlank(user.getEmail())) {
            // 这种情况下无法验证邮箱验证码，因为不知道发给谁（或者验证码服务里存了 session？）
            // 简单起见，如果用户没邮箱，报错
            throw new AppException(ResultCode.PARAM_ERROR); // 或者具体的 "未绑定邮箱"
        }

        // 2. 校验验证码 (Mock: 使用用户邮箱验证)
        verifyCode(user.getEmail(), dto.getCode());

        // 3. 检查是否已设置过密码 (Apifox 文档说: "未设置过密码的用户...")
        if (StrUtil.isNotBlank(user.getPassword())) {
            throw new AppException(ResultCode.PASSWORD_ALREADY_SET);
        }

        if (dto.getPassword().length() < 6) {
            throw new AppException(ResultCode.PASSWORD_STRENGTH_ERROR);
        }

        String hashed = BCrypt.hashpw(dto.getPassword());
        user.setPassword(hashed);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UserPasswordChangeDTO dto) {
        User user = getCurrentUser();

        // 1. 验证旧密码
        if (user.getPassword() == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new AppException(ResultCode.OLD_PASSWORD_ERROR);
        }

        // 2. 验证新密码强度
        if (dto.getNewPassword().length() < 6) {
            throw new AppException(ResultCode.PASSWORD_STRENGTH_ERROR);
        }

        // 3. 更新密码
        String hashed = BCrypt.hashpw(dto.getNewPassword());
        user.setPassword(hashed);
        userMapper.updateById(user);
    }

    /**
     * 获取关注列表
     */
    @Override
    public Map<String, Object> getFollowList(String userIdStr, Integer page, Integer size) {
        // 1. 参数处理
        long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new AppException(ResultCode.PARAM_ERROR, "用户ID格式错误");
        }

        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 2. 构建分页请求，按关注时间倒序 (最新关注的在最前)
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 3. 查询 MongoDB
        Page<UserFollowDoc> docPage = userFollowRepository.findByUserId(userId, pageable);

        // 4. 转换数据格式
        // UserFollowDoc 中：userId 是"我"，targetUserId 是"我关注的人"
        // 我们需要返回 targetUser 的信息
        List<Map<String, String>> records = docPage.getContent().stream().map(doc -> {
            Map<String, String> item = new HashMap<>();
            // 转 String 防止前端精度丢失
            item.put("userId", String.valueOf(doc.getTargetUserId()));
            // 使用 Mongo 中的冗余字段，避免回查 Postgres，提高性能
            item.put("nickname", doc.getTargetUserNickname());
            item.put("avatar", doc.getTargetUserAvatar());
            return item;
        }).collect(Collectors.toList());

        // 5. 构建返回体
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", docPage.getTotalElements());
        // result.put("current", docPage.getNumber() + 1); // 可选
        // result.put("size", docPage.getSize()); // 可选

        return result;
    }

    /**
     * 获取粉丝列表实现
     */
    @Override
    public Map<String, Object> getFanList(String userIdStr, Integer page, Integer size) {
        // 1. 参数处理
        long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new AppException(ResultCode.PARAM_ERROR, "用户ID格式错误");
        }

        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 2. 构建分页请求，按关注时间倒序 (最新关注的粉丝在最前)
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 3. 查询 MongoDB (查找 targetUserId 为当前用户ID 的记录)
        Page<UserFollowDoc> docPage = userFollowRepository.findByTargetUserId(userId, pageable);

        // 4. 转换数据格式
        // UserFollowDoc 中：targetUserId 是"我"，userId 是"关注我的人(粉丝)"
        // 所以这里我们需要返回 userId, userNickname, userAvatar
        List<Map<String, String>> records = docPage.getContent().stream().map(doc -> {
            Map<String, String> item = new HashMap<>();
            // 转 String 防止前端精度丢失
            item.put("userId", String.valueOf(doc.getUserId()));
            // 粉丝的信息存储在 user系列字段中 (因为是他发起的关注)
            item.put("nickname", doc.getUserNickname());
            item.put("avatar", doc.getUserAvatar());
            return item;
        }).collect(Collectors.toList());

        // 5. 构建返回体
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", docPage.getTotalElements());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(String targetUserIdStr) {
        Long currentUserId = UserContext.getUserId();
        long targetUserId;
        try {
            targetUserId = Long.parseLong(targetUserIdStr);
        } catch (NumberFormatException e) {
            throw new AppException(ResultCode.PARAM_ERROR);
        }

        // 1. 不能关注自己
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ResultCode.PARAM_ERROR, "不能关注自己");
        }

        // 2. 检查是否已关注 (幂等性)
        if (userFollowRepository.existsByUserIdAndTargetUserId(currentUserId, targetUserId)) {
            return; // 已经关注了，直接返回成功，或者抛异常提示
        }

        // 3. 获取双方信息 (为了填充 Mongo 冗余字段)
        // 使用 Postgres 查询，确保数据最新
        User currentUser = userMapper.selectById(currentUserId);
        User targetUser = userMapper.selectById(targetUserId);

        if (targetUser == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND, "关注的用户不存在");
        }

        // 4. 构建文档并保存
        UserFollowDoc followDoc = new UserFollowDoc();
        followDoc.setUserId(currentUserId);
        followDoc.setTargetUserId(targetUserId);

        // 填充粉丝信息 (我)
        followDoc.setUserNickname(currentUser.getNickname());
        followDoc.setUserAvatar(currentUser.getAvatar());

        // 填充博主信息 (对方)
        followDoc.setTargetUserNickname(targetUser.getNickname());
        followDoc.setTargetUserAvatar(targetUser.getAvatar());

        followDoc.setCreatedAt(java.time.LocalDateTime.now());

        userFollowRepository.save(followDoc);

        // 发送 RabbitMQ 消息 (InteractionEvent)
        InteractionEvent event = new InteractionEvent(
                currentUserId,
                targetUserIdStr,
                "FOLLOW",
                "ADD",
                null
        );
        // 路由键: interaction.create
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.create", event);
    }

    @Override
    public void unfollowUser(String targetUserIdStr) {
        Long currentUserId = UserContext.getUserId();
        long targetUserId;
        try {
            targetUserId = Long.parseLong(targetUserIdStr);
        } catch (NumberFormatException e) {
            throw new AppException(ResultCode.PARAM_ERROR);
        }

        userFollowRepository.deleteByUserIdAndTargetUserId(currentUserId, targetUserId);
    }

    @Override
    public List<UserInfo> getFriendList() {
        Long currentUserId = UserContext.getUserId();

        // 逻辑：好友 = (我关注的人) ∩ (关注我的人)

        // 1. 先查出 "我关注了谁" (只查 ID 即可，省流量)
        List<UserFollowDoc> myFollowings = userFollowRepository.findFollowingIds(currentUserId);
        if (CollUtil.isEmpty(myFollowings)) {
            return new ArrayList<>();
        }

        // 提取出我关注的人的 ID 列表
        List<Long> followingIds = myFollowings.stream()
                .map(UserFollowDoc::getTargetUserId)
                .collect(Collectors.toList());

        // 2. 查 "这些 ID 里，谁关注了我"
        // 这里的 userIdIn 是潜在的好友(我关注的人)，targetUserId 是我
        List<UserFollowDoc> mutualFollows = userFollowRepository.findByUserIdInAndTargetUserId(followingIds, currentUserId);

        // 3. 转换结果
        // mutualFollows 里的每一条记录：
        // userId = 好友ID (因为是他关注了我)
        // userNickname = 好友昵称
        // targetUserId = 我
        return mutualFollows.stream().map(doc -> {
            UserInfo info = new UserInfo();
            info.setUserId(String.valueOf(doc.getUserId()));
            info.setNickname(doc.getUserNickname());
            info.setAvatar(doc.getUserAvatar());
            // info.setEmail(...) // 好友列表通常不需要展示邮箱
            return info;
        }).collect(Collectors.toList());
    }
    // Private Helpers

    private User getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserProfileVO buildProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();

        // 1. 复制基础信息 (PostgreSQL)
        vo.setUserId(user.getId().toString());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setGender(user.getGender());
        if (user.getBirthday() != null) {
            vo.setBirthday(user.getBirthday().toString());
        }
        vo.setRegion(user.getRegion());
        vo.setEmail(user.getEmail());
        vo.setHasPassword(StrUtil.isNotBlank(user.getPassword()));

        // =====================================================
        // 2. 查询统计数据 (MongoDB)
        // =====================================================
        Long userId = user.getId();

        // 2.1 查询关注数 (我关注了谁 -> userId = 我)
        long followCount = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId)),
                UserFollowDoc.class
        );
        vo.setFollowCount(followCount);

        // 2.2 查询粉丝数 (谁关注了我 -> targetUserId = 我)
        long fanCount = mongoTemplate.count(
                Query.query(Criteria.where("targetUserId").is(userId)),
                UserFollowDoc.class
        );
        vo.setFanCount(fanCount);

        // 2.3 查询获赞总数 (聚合查询)
        // 逻辑：找出该用户所有未删除的帖子，累加它们的 likeCount
        Aggregation agg = Aggregation.newAggregation(
                // 筛选：我的帖子 && 未删除
                Aggregation.match(Criteria.where("userId").is(userId).and("isDeleted").is(0)),
                // 分组：求和
                Aggregation.group().sum("likeCount").as("totalLikes")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, PostDoc.class, Map.class);
        Map<String, Object> result = results.getUniqueMappedResult();

        if (result != null && result.get("totalLikes") != null) {
            // Mongo 聚合返回的数值类型可能不固定，转 Number 再取 longValue 比较稳妥
            vo.setReceivedLikeCount(((Number) result.get("totalLikes")).longValue());
        } else {
            vo.setReceivedLikeCount(0L);
        }

        return vo;
    }
    // 实现了邮箱验证码的逻辑
    private void verifyCode(String target, String code) {
        // 修改了api接口，只能进行绑定邮箱
        String redisKey = "verify:code:" + target; // 假设只验证绑定类型的码

        String cacheCode = redisTemplate.opsForValue().get(redisKey);

        if (cacheCode == null || !cacheCode.equals(code)) {
            throw new AppException(ResultCode.VERIFY_CODE_ERROR);
        }

        // 还是删除好了
        redisTemplate.delete(redisKey);
    }

    @Override
    public List<UserSearchVO> searchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }

        // 1. [PostgreSQL] 模糊查询用户 (限制 20 条，防止返回过多数据)
        // 逻辑：nickname LIKE %keyword% OR email LIKE %keyword%
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(w -> w.like(User::getNickname, keyword)
                        .or()
                        .like(User::getEmail, keyword)) // 支持搜邮箱
                .eq(User::getStatus, 1) // 只搜正常状态的用户
                .last("LIMIT 20"));     // 硬限制数量

        if (CollUtil.isEmpty(users)) {
            return new ArrayList<>();
        }

        // 2. 准备 ID 列表
        Long currentUserId = UserContext.getUserId();
        List<Long> searchedUserIds = users.stream().map(User::getId).collect(Collectors.toList());

        // 用于快速查找的 Set
        Set<Long> myFollowingSet = new HashSet<>(); // 我关注了谁
        Set<Long> myFansSet = new HashSet<>();      // 谁关注了我

        // 3. [MongoDB] 批量查询关系状态 (只有登录用户才查)
        if (currentUserId != null) {
            // A. 查 "我关注了其中哪些人"
            // userId = 我, targetUserId IN (搜索结果IDs)
            List<UserFollowDoc> followings = userFollowRepository.findByUserIdAndTargetUserIdIn(currentUserId, searchedUserIds);
            myFollowingSet = followings.stream().map(UserFollowDoc::getTargetUserId).collect(Collectors.toSet());

            // B. 查 "其中哪些人关注了我"
            // userId IN (搜索结果IDs), targetUserId = 我
            List<UserFollowDoc> fans = userFollowRepository.findByUserIdInAndTargetUserId(searchedUserIds, currentUserId);
            myFansSet = fans.stream().map(UserFollowDoc::getUserId).collect(Collectors.toSet());
        }

        // 4. 组装 VO
        Set<Long> finalMyFollowingSet = myFollowingSet;
        Set<Long> finalMyFansSet = myFansSet;

        return users.stream().map(user -> {
            UserSearchVO vo = new UserSearchVO();
            // 基础信息
            vo.setUserId(String.valueOf(user.getId()));
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setEmail(user.getEmail()); // 搜索结果通常可以展示邮箱辅助确认

            // 关系状态
            if (currentUserId != null && currentUserId.equals(user.getId())) {
                // 搜到了自己
                vo.setIsFollowed(false);
                vo.setIsFollowingMe(false);
            } else {
                vo.setIsFollowed(finalMyFollowingSet.contains(user.getId()));
                vo.setIsFollowingMe(finalMyFansSet.contains(user.getId()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // ================== 1. 获取我的点赞列表 ==================
    @Override
    public Map<String, Object> getMyLikeList(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        // 按时间倒序
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 查关系表
        Page<PostLikeDoc> docPage = postLikeRepository.findByUserId(userId, pageable);

        // 提取 ID 列表
        List<String> postIds = docPage.getContent().stream()
                .map(PostLikeDoc::getPostId)
                .collect(Collectors.toList());

        // 转换并返回
        return buildPostListResult(postIds, docPage.getTotalElements(), null);
    }

    // ================== 2. 获取我的收藏列表 ==================
    @Override
    public Map<String, Object> getMyCollectList(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostCollectDoc> docPage = postCollectRepository.findByUserId(userId, pageable);

        List<String> postIds = docPage.getContent().stream()
                .map(PostCollectDoc::getPostId)
                .collect(Collectors.toList());

        return buildPostListResult(postIds, docPage.getTotalElements(), null);
    }

    // ================== 3. 获取我的评分列表 ==================
    @Override
    public Map<String, Object> getMyRateList(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostRatingDoc> docPage = postRatingRepository.findByUserId(userId, pageable);

        List<String> postIds = new ArrayList<>();
        // 建立 postId -> score 的映射，方便后面填入 VO
        Map<String, Double> scoreMap = new HashMap<>();

        for (PostRatingDoc doc : docPage.getContent()) {
            postIds.add(doc.getPostId());
            scoreMap.put(doc.getPostId(), doc.getScore());
        }

        return buildPostListResult(postIds, docPage.getTotalElements(), scoreMap);
    }

    // ================== 4. 获取我的帖子列表 ==================
    @Override
    public Map<String, Object> getMyPostList(Integer type, Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostDoc> docPage;
        // 如果前端传了 type (0或1)，则按类型查；否则查全部
        if (type != null) {
            docPage = postRepository.findByUserIdAndTypeAndIsDeleted(userId, type, 0, pageable);
        } else {
            docPage = postRepository.findByUserIdAndIsDeleted(userId, 0, pageable);
        }

        // 这里不需要 buildPostListResult 的“查库”逻辑，因为我们已经拿到了 PostDoc
        // 直接复用 PostServiceImpl 里的 convertToVO 逻辑有点麻烦，
        // 我们直接在这里手动转一下简化版 VO (复用 buildPostListResult 内部的转换逻辑)

        List<PostVO> records = docPage.getContent().stream().map(doc -> {
            // 简单组装 VO
            PostVO vo = new PostVO();
            vo.setId(doc.getId());
            vo.setTitle(doc.getTitle());
            vo.setType(doc.getType());
            vo.setCover(doc.getCover());
            vo.setLikeCount(doc.getLikeCount());
            vo.setTags(doc.getTags());

            UserInfo author = new UserInfo();
            author.setUserId(String.valueOf(doc.getUserId()));
            author.setNickname(doc.getUserNickname());
            author.setAvatar(doc.getUserAvatar());
            vo.setAuthor(author);

            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", docPage.getTotalElements());
        return result;
    }

    // ================== 5. 获取我的浏览历史 ==================
    @Override
    public Map<String, Object> getBrowsingHistory(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        // 按浏览时间倒序
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "viewTime"));

        Page<PostViewHistoryDoc> historyPage = postViewHistoryRepository.findByUserId(userId, pageable);

        List<String> postIds = historyPage.getContent().stream()
                .map(PostViewHistoryDoc::getPostId)
                .collect(Collectors.toList());

        // 使用之前的通用方法查详情
        // 注意：通用方法查出来的列表顺序可能跟 postIds 不一致，这里为了严谨，最好在内存重排一下
        // 但为了代码简单，暂时直接返回
        return buildPostListResult(postIds, historyPage.getTotalElements(), null);
    }

    @Override
    public void recordBrowsingHistory(Long userId, String postId) {
        // 【新增修复】使用 MongoTemplate 的 Upsert (更新或插入) 原子操作
        // 解决高并发下的 DuplicateKeyException 问题

        // 1. 定义查询条件: userId + postId
        Query query = new Query(Criteria.where("userId").is(userId).and("postId").is(postId));

        // 2. 定义更新内容: 更新 viewTime 为当前时间
        Update update = new Update();
        update.set("viewTime", LocalDateTime.now());
        // 如果想记录浏览次数，也可以加上: update.inc("viewCount", 1);

        // 3. 执行 Upsert
        // 如果记录存在，则更新 viewTime；如果不存在，则插入一条新记录
        mongoTemplate.upsert(query, update, PostViewHistoryDoc.class);
    }

    // ================== 私有通用方法：批量查帖子并转 VO ==================
    private Map<String, Object> buildPostListResult(List<String> postIds, long total, Map<String, Double> scoreMap) {
        // 1. 判空快速返回
        if (CollUtil.isEmpty(postIds)) {
            Map<String, Object> res = new HashMap<>();
            res.put("records", Collections.emptyList());
            res.put("total", 0);
            return res;
        }

        // 2. 批量查帖子详情
        List<PostDoc> posts = postRepository.findAllById(postIds);

        // 3. 将 List 转为 Map，解决 Mongo 返回乱序问题
        Map<String, PostDoc> postMap = posts.stream()
                .collect(Collectors.toMap(PostDoc::getId, p -> p));

        // 4. 批量查询交互状态 (点赞/收藏)
        // 避免在循环中查库 (N+1问题)，一次性查出这批帖子中我点赞/收藏了哪些
        Set<String> likedPostIds = new HashSet<>();
        Set<String> collectedPostIds = new HashSet<>();
        Long currentUserId = UserContext.getUserId();

        if (currentUserId != null) {
            // 查点赞
            List<PostLikeDoc> likes = postLikeRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            likedPostIds = likes.stream().map(PostLikeDoc::getPostId).collect(Collectors.toSet());

            // 查收藏
            List<PostCollectDoc> collects = postCollectRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            collectedPostIds = collects.stream().map(PostCollectDoc::getPostId).collect(Collectors.toSet());
        }

        // 5. 组装 VO 列表 (保持 postIds 的原始时间顺序)
        // 为了 Lambda 表达式能访问非 final 变量，这里使用临时变量
        Set<String> finalLikedIds = likedPostIds;
        Set<String> finalCollectedIds = collectedPostIds;

        List<PostVO> voList = postIds.stream()
                .map(postMap::get) // 从 Map 取值
                .filter(Objects::nonNull) // 过滤物理删除
                .filter(doc -> {
                    // 过滤逻辑删除
                    return doc.getIsDeleted() == null || doc.getIsDeleted() == 0;
                })
                .map(doc -> {
                    PostVO vo = new PostVO();
                    vo.setId(doc.getId());
                    vo.setTitle(doc.getTitle());
                    vo.setType(doc.getType());
                    vo.setCover(doc.getCover()); // 使用封面

                    // 填充计数
                    vo.setLikeCount(doc.getLikeCount());
                    vo.setCollectCount(doc.getCollectCount());
                    // 如果需要 commentCount 也在这里设置

                    // 【新增修复】填充交互状态
                    vo.setIsLiked(finalLikedIds.contains(doc.getId()));
                    vo.setIsCollected(finalCollectedIds.contains(doc.getId()));
                    // 关注状态(isFollowed) 如果需要也可以在这里批量查，方法同上

                    // 填充作者
                    UserInfo author = new UserInfo();
                    author.setUserId(String.valueOf(doc.getUserId()));
                    author.setNickname(doc.getUserNickname());
                    author.setAvatar(doc.getUserAvatar());
                    vo.setAuthor(author);

                    // 填充我的评分
                    if (scoreMap != null && scoreMap.containsKey(doc.getId())) {
                        vo.setMyScore(scoreMap.get(doc.getId()));
                    }

                    // 填充时间
                    if (doc.getCreatedAt() != null) {
                        vo.setCreatedAt(doc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    }

                    return vo;
                })
                .collect(Collectors.toList());

        // 6. 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("records", voList);
        result.put("total", total);
        return result;
    }
}
