package com.szu.afternoon3.platform.controller;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.json.JSONUtil;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.UserFollowRepository;
import com.szu.afternoon3.platform.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 保证 PG 数据库回滚
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserFollowRepository userFollowRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private String adminToken;
    private String userToken;
    private Long adminId;
    private Long normalUserId;
    private Long targetUserId; // 用于测试删除的目标用户

    @BeforeEach
    public void setup() {
        // 1. 清理 MongoDB (PG 会自动回滚)
        mongoTemplate.dropCollection(PostDoc.class);
        mongoTemplate.dropCollection(UserFollowDoc.class);

        // 2. 创建管理员账号
        User admin = new User();
        admin.setEmail("admin@szu.edu.cn");
        admin.setNickname("AdminUser");
        String hashed = BCrypt.hashpw("123456");
        admin.setPassword(hashed);
        admin.setRole("ADMIN"); // 关键：角色为 ADMIN
        admin.setStatus(1);
        userMapper.insert(admin);
        this.adminId = admin.getId();
        this.adminToken = "Bearer " + jwtUtil.createAccessToken(admin.getId(), "ADMIN", admin.getNickname());

        // 3. 创建普通用户 (尝试越权)
        User user = new User();
        user.setEmail("hacker@szu.edu.cn");
        user.setNickname("Hacker");
        user.setRole("USER"); // 关键：角色为 USER
        user.setStatus(1);
        userMapper.insert(user);
        this.userToken = "Bearer " + jwtUtil.createAccessToken(user.getId(), "USER", user.getNickname());

        // 4. 创建目标用户 (用于被删除/查看详情)
        User target = new User();
        target.setEmail("target@szu.edu.cn");
        target.setNickname("TargetUser");
        target.setRole("USER");
        target.setStatus(1);
        userMapper.insert(target);
        this.normalUserId = target.getId();

        // 5. 制造 MongoDB 数据 (用于测试聚合查询)
        // 目标用户发了 2 个帖子
        createPost(normalUserId, "Post A", 10, 1); // 10赞
        createPost(normalUserId, "Post B", 5, 1);  // 5赞

        // 目标用户被管理员关注 (测试粉丝数)
        UserFollowDoc follow = new UserFollowDoc();
        follow.setUserId(adminId);
        follow.setTargetUserId(normalUserId);
        follow.setCreatedAt(LocalDateTime.now());
        userFollowRepository.save(follow);
    }

    @AfterEach
    public void tearDown() {
        mongoTemplate.dropCollection(PostDoc.class);
        mongoTemplate.dropCollection(UserFollowDoc.class);
    }

    private void createPost(Long userId, String title, int likes, int status) {
        PostDoc post = new PostDoc();
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent("Content of " + title);
        post.setLikeCount(likes);
        post.setStatus(status); // 1:发布, 0:审核中
        post.setIsDeleted(0);
        post.setCreatedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    // ==================== 1. 鉴权测试 ====================

    @Test
    @DisplayName("测试：普通用户访问后台接口应被拦截 (403)")
    public void testPermissionDenied() throws Exception {
        AdminUserSearchDTO dto = new AdminUserSearchDTO();
        mockMvc.perform(post("/admin/user/list")
                        .header("Authorization", userToken) // 使用普通用户 Token
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40302)) // PERMISSION_DENIED
                .andExpect(jsonPath("$.message").value("非管理员无权操作"));
    }

    @Test
    @DisplayName("测试：管理员登录成功并获取 Token")
    public void testAdminLogin() throws Exception {
        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setAccount("admin@szu.edu.cn");
        loginDTO.setPassword("123456");

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.userInfo.userId").value(String.valueOf(adminId)));
    }

    // ==================== 2. 用户管理测试 ====================

    @Test
    @DisplayName("测试：用户列表聚合查询 (验证统计数据正确性)")
    public void testGetUserList() throws Exception {
        AdminUserSearchDTO dto = new AdminUserSearchDTO();
        dto.setNickname("Target"); // 搜索目标用户

        mockMvc.perform(post("/admin/user/list")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                // 验证统计数据：发了2个贴，总赞数10+5=15，粉丝数1(管理员关注了他)
                .andExpect(jsonPath("$.data.records[0].postCount").value(2))
                .andExpect(jsonPath("$.data.records[0].likeCount").value(15))
                .andExpect(jsonPath("$.data.records[0].fanCount").value(1));
    }

    @Test
    @DisplayName("测试：获取用户详情 (AdminUserDetailVO)")
    public void testGetUserDetail() throws Exception {
        mockMvc.perform(get("/admin/users/" + normalUserId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("TargetUser"))
                .andExpect(jsonPath("$.data.receivedLikeCount").value(15)) // 验证被动影响力
                .andExpect(jsonPath("$.data.fanCount").value(1));
    }

    @Test
    @DisplayName("测试：删除用户及其关联数据 (验证异步清理)")
    public void testDeleteUser() throws Exception {
        AdminUserDeleteDTO dto = new AdminUserDeleteDTO();
        dto.setReason("违规用户");

        // 1. 执行删除
        mockMvc.perform(post("/admin/user/" + normalUserId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(dto)))
                .andExpect(status().isOk());

        // 2. 验证 PG 用户已删除
        Assertions.assertNull(userMapper.selectById(normalUserId), "PostgreSQL 中的用户应被删除");

        // 3. 验证 Mongo 数据清理 (由于是异步 @Async，需要稍微等待)
        Thread.sleep(1000);

        long postCount = postRepository.count(); // 之前有2个贴
        long followCount = userFollowRepository.count(); // 之前有1个关注

        // 应该是 0，因为帖子作者被删了
        Assertions.assertEquals(0, postCount, "该用户的帖子应该被级联删除");
        // 应该是 0，因为关注关系的 targetUser 被删了
        Assertions.assertEquals(0, followCount, "该用户的关注关系应该被级联删除");
    }

    // ==================== 3. 内容审核测试 ====================

    @Test
    @DisplayName("测试：内容审核列表与审核操作")
    public void testAuditPost() throws Exception {
        // 1. 创建一个待审核帖子
        PostDoc pendingPost = new PostDoc();
        pendingPost.setUserId(normalUserId);
        pendingPost.setTitle("待审核");
        pendingPost.setStatus(0); // 待审核
        pendingPost.setIsDeleted(0);
        postRepository.save(pendingPost);
        String postId = pendingPost.getId();

        // 2. 审核通过 (Pass)
        AdminPostAuditDTO auditDTO = new AdminPostAuditDTO();
        auditDTO.setStatus(1); // 通过
        auditDTO.setReason("合规");

        mockMvc.perform(post("/admin/post/" + postId + "/audit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(auditDTO)))
                .andExpect(status().isOk());

        // 3. 验证状态变更
        PostDoc updatedPost = postRepository.findById(postId).orElseThrow();
        Assertions.assertEquals(1, updatedPost.getStatus(), "帖子状态应变更为 1 (已发布)");

        // 4. 审核拒绝 (Reject)
        auditDTO.setStatus(2); // 拒绝
        mockMvc.perform(post("/admin/post/" + postId + "/audit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(auditDTO)))
                .andExpect(status().isOk());

        updatedPost = postRepository.findById(postId).orElseThrow();
        Assertions.assertEquals(2, updatedPost.getStatus(), "帖子状态应变更为 2 (拒绝)");
    }
}