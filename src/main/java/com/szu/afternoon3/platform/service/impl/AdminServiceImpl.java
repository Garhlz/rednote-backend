package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        String token = jwtUtil.createToken(user.getId());
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

    // 为了满足“显示用户组身份及注册时间”，我们可能需要返回更详细的VO
    // 暂时先用 UserInfo，如果前端需要 createdAt，可以扩展 UserInfo 或返回 Map
    // 既然要求严格，我直接扩展一个 AdminProfileVO 吧，或者用 Map 返回

    public Map<String, Object> getAdminProfileFull() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getId().toString());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }

    @Override
    public Map<String, Object> getUserList(AdminUserSearchDTO dto) {
        // MyBatis Plus 分页
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

        // 只查询普通用户? 需求没说，通常包括所有用户。

        IPage<User> userPage = userMapper.selectPage(page, wrapper);

        List<AdminUserVO> records = new ArrayList<>();
        for (User user : userPage.getRecords()) {
            AdminUserVO vo = new AdminUserVO();
            BeanUtils.copyProperties(user, vo);
            vo.setRegisterTime(user.getCreatedAt());

            // 统计数据
            fillUserStats(vo, user.getId());
            records.add(vo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", userPage.getTotal());
        return result;
    }

    private void fillUserStats(AdminUserVO vo, Long userId) {
        // 1. 帖子数
        long postCount = mongoTemplate.count(new Query(Criteria.where("userId").is(userId).and("isDeleted").is(0)),
                PostDoc.class);
        vo.setPostCount(postCount);

        // 2. 粉丝数 (关注我的人)
        long fanCount = mongoTemplate.count(new Query(Criteria.where("targetUserId").is(userId)), UserFollowDoc.class);
        vo.setFanCount(fanCount);

        // 3. 关注数 (我关注的人)
        long followCount = mongoTemplate.count(new Query(Criteria.where("userId").is(userId)), UserFollowDoc.class);
        vo.setFollowCount(followCount);

        // 4. 获赞数 & 均分 (聚合查询)
        // 聚合 PostDoc，match userId, sum likeCount, avg ratingAverage
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId).and("isDeleted").is(0)),
                Aggregation.group().sum("likeCount").as("totalLikes").avg("ratingAverage").as("avgScore"));

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "posts", Map.class);
        Map map = results.getUniqueMappedResult();

        if (map != null) {
            Number totalLikes = (Number) map.get("totalLikes");
            Number avgScore = (Number) map.get("avgScore");
            vo.setLikeCount(totalLikes != null ? totalLikes.longValue() : 0L);
            vo.setAvgScore(avgScore != null ? avgScore.doubleValue() : 0.0);
        } else {
            vo.setLikeCount(0L);
            vo.setAvgScore(0.0);
        }
    }

    @Override
    public void deleteUser(Long userId, String reason) {
        // 逻辑删除
        User user = userMapper.selectById(userId);
        if (user == null)
            return;

        userMapper.deleteById(userId);

        // 可以在这里记录操作日志 (reason)
        log.info("Admin deleted user {}: {}", userId, reason);
    }

    @Override
    public Map<String, Object> getPostList(AdminPostSearchDTO dto) {
        Query query = new Query();

        // 筛选未删除
        query.addCriteria(Criteria.where("isDeleted").is(0));

        // 关键词搜索
        if (StrUtil.isNotBlank(dto.getTitleKeyword())) {
            query.addCriteria(Criteria.where("title").regex(dto.getTitleKeyword(), "i"));
        }

        // 标签
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            query.addCriteria(Criteria.where("tags").all(dto.getTags()));
        }

        // 时间范围
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            query.addCriteria(Criteria.where("createdAt")
                    .gte(dto.getStartTime().atStartOfDay())
                    .lte(dto.getEndTime().atTime(LocalTime.MAX)));
        }

        // 用户筛选 (昵称或邮箱)
        if (StrUtil.isNotBlank(dto.getEmail())) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail()));
            if (user != null) {
                query.addCriteria(Criteria.where("userId").is(user.getId()));
            } else {
                // 查不到用户，直接返回空
                return Map.of("records", new ArrayList<>(), "total", 0);
            }
        } else if (StrUtil.isNotBlank(dto.getNickname())) {
            // 模糊匹配昵称 -> 找到一堆 userId
            // 或者直接用 PostDoc 里的 userNickname (不严谨但快)
            // 鉴于 PostDoc 有 userNickname，直接查它
            query.addCriteria(Criteria.where("userNickname").regex(dto.getNickname(), "i"));
        }

        // 分页
        long total = mongoTemplate.count(query, PostDoc.class);
        Pageable pageable = PageRequest.of(dto.getPage() - 1, dto.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(pageable);

        List<PostDoc> docs = mongoTemplate.find(query, PostDoc.class);

        List<AdminPostVO> records = docs.stream().map(this::convertToAdminPostVO).collect(Collectors.toList());

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
}
