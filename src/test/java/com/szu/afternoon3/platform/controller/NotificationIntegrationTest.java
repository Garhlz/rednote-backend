package com.szu.afternoon3.platform.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONObject;
import com.szu.afternoon3.platform.common.RedisKey;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.NotificationRepository;
import com.szu.afternoon3.platform.repository.PostLikeRepository;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
//@Transactional // 自动回滚 PostgreSQL 数据
public class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserMapper userMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private StringRedisTemplate redisTemplate;

    // MongoDB Repositories
    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User sender;   // 点赞的人 (小青)
    private User receiver; // 帖子的作者 (悠悠)
    private String senderToken;
    private String receiverToken;
    private String postId;

    @BeforeEach
    public void setup() {
        // 1. 清理 MongoDB 和 Redis 脏数据
        clearNoSqlData();

        // 2. 创建两个测试用户 (PostgreSQL)
        // 发送者
        sender = createUser("Sender_XiaoQing");
        senderToken = "Bearer " + jwtUtil.createToken(sender.getId());

        // 接收者
        receiver = createUser("Receiver_YouYou");
        receiverToken = "Bearer " + jwtUtil.createToken(receiver.getId());

        // 3. 接收者发一篇帖子 (MongoDB)
        PostDoc post = new PostDoc();
        post.setUserId(receiver.getId());
        post.setTitle("Arch Linux 安装心得");
        post.setContent("pacman -Syu 非常快...");
        post.setType(2); // 纯文字
        post.setStatus(1); // 已发布
        post.setIsDeleted(0);
        post.setTags(List.of("Linux", "Arch"));
        post.setCreatedAt(LocalDateTime.now());
        postRepository.save(post);
        this.postId = post.getId();
    }

    @AfterEach
    public void tearDown() {
        // 1. 清理 NoSQL
        clearNoSqlData();

        // 2. 【第二步：新增手动清理 SQL 数据】
        if (sender != null) userMapper.deleteById(sender.getId());
        if (receiver != null) userMapper.deleteById(receiver.getId());
    }

    private void clearNoSqlData() {
        notificationRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        if (postId != null) {
            redisTemplate.delete(RedisKey.POST_LIKE_SET + postId);
        }
    }


    private User createUser(String nickname) {
        User user = new User();
        user.setNickname(nickname);
        user.setEmail(UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    @Test
    @DisplayName("测试完整流程：点赞 -> 生成通知 -> 轮询未读 -> 列表查询 -> 一键已读")
    public void testNotificationFlow() throws Exception {
        // ==========================================
        // Step 1: 小青(Sender) 点赞 悠悠(Receiver) 的帖子
        // ==========================================
        JSONObject likeBody = new JSONObject();
        likeBody.set("postId", postId);

        mockMvc.perform(post("/api/interaction/like/post")
                        .header("Authorization", senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(likeBody.toString()))
                .andExpect(status().isOk());

        // 【关键】因为 Listener 是 @Async 异步执行的，我们需要等待一会儿
        Thread.sleep(1500);

        // 验证 MongoDB 是否生成了通知
        List<NotificationDoc> notifications = notificationRepository.findAll();
        Assertions.assertEquals(1, notifications.size(), "应该生成一条通知");
        NotificationDoc notice = notifications.get(0);
        Assertions.assertEquals(receiver.getId(), notice.getReceiverId(), "接收者应该是悠悠");
        Assertions.assertEquals(sender.getId(), notice.getSenderId(), "发送者应该是小青");
        Assertions.assertEquals("LIKE_POST", notice.getType(), "类型应该是点赞");
        Assertions.assertEquals(false, notice.getIsRead(), "默认应该是未读");

        // ==========================================
        // Step 2: 悠悠(Receiver) 轮询未读数
        // ==========================================
        mockMvc.perform(get("/api/message/unread-count")
                        .header("Authorization", receiverToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1)); // 期望未读数为 1

        // ==========================================
        // Step 3: 悠悠(Receiver) 查看消息列表
        // ==========================================
        mockMvc.perform(get("/api/message/notifications")
                        .header("Authorization", receiverToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].targetPreview").value("Arch Linux 安装心得")) // 验证摘要
                .andExpect(jsonPath("$.data.records[0].senderNickname").value("Sender_XiaoQing"));

        // ==========================================
        // Step 4: 悠悠(Receiver) 点击一键已读
        // ==========================================
        mockMvc.perform(post("/api/message/read")
                        .header("Authorization", receiverToken))
                .andExpect(status().isOk());

        // 再次轮询未读数，应该变为 0
        mockMvc.perform(get("/api/message/unread-count")
                        .header("Authorization", receiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));

        // 验证数据库状态
        NotificationDoc updatedNotice = notificationRepository.findById(notice.getId()).orElseThrow();
        Assertions.assertTrue(updatedNotice.getIsRead(), "数据库字段应更新为已读");
    }

    @Test
    @DisplayName("测试自我操作不通知：自己赞自己")
    public void testSelfLikeNoNotification() throws Exception {
        // 悠悠自己赞自己的帖子
        JSONObject likeBody = new JSONObject();
        likeBody.set("postId", postId);

        mockMvc.perform(post("/api/interaction/like/post")
                        .header("Authorization", receiverToken) // 使用作者自己的Token
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(likeBody.toString()))
                .andExpect(status().isOk());

        Thread.sleep(1000);

        // 验证：不应该生成通知
        long count = notificationRepository.count();
        Assertions.assertEquals(0, count, "自己赞自己不应生成通知");
    }
}