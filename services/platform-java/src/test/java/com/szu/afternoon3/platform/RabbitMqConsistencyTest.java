package com.szu.afternoon3.platform;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.UserProfileUpdateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
@ActiveProfiles("dev") // 确保使用 dev 配置连接本地 RabbitMQ
public class RabbitMqConsistencyTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PostRepository postRepository;

    private Long testUserId;
    private String testPostId;

    @BeforeEach
    public void setup() {
        // 1. 在 PostgreSQL 创建一个测试用户
        User user = new User();
        user.setNickname("旧名字_OldName");
        user.setEmail("mq_test_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com");
        user.setStatus(1);
        userMapper.insert(user);
        this.testUserId = user.getId();

        // 2. 在 MongoDB 创建一个属于该用户的帖子 (存入旧昵称)
        PostDoc post = new PostDoc();
        post.setUserId(testUserId);
        post.setUserNickname("旧名字_OldName"); // 冗余字段
        post.setTitle("RabbitMQ 测试贴");
        post.setContent("测试数据一致性");
        post.setIsDeleted(0);
        post.setCreatedAt(LocalDateTime.now());
        postRepository.save(post);
        this.testPostId = post.getId();

        // 3. 模拟登录上下文
        UserContext.setUserId(testUserId);
    }

    @AfterEach
    public void tearDown() {
        // 清理数据
        if (testUserId != null) {
            userMapper.deleteById(testUserId);
        }
        if (testPostId != null) {
            postRepository.deleteById(testPostId);
        }
        UserContext.clear();
    }

    @Test
    public void testUserProfileUpdateConsistency() {
        System.out.println("🚀 开始测试：修改用户资料 -> RabbitMQ -> MongoDB 同步");

        // 1. 执行动作：修改用户昵称
        UserProfileUpdateDTO updateDTO = new UserProfileUpdateDTO();
        updateDTO.setNickname("新名字_NewName_MQ");
        
        // 这行代码会向 RabbitMQ 发送 user.update 消息
        userService.updateProfile(updateDTO);

        System.out.println("✅ 消息已发送，等待消费者处理...");

        // 2. 验证结果 (使用 Awaitility 或 Thread.sleep 等待异步处理)
        // RabbitMQ 是异步的，不能立即查到结果，给它最多 5 秒时间
        try {
            // 简单起见使用 Thread.sleep，生产级测试推荐用 Awaitility
            // await().atMost(5, TimeUnit.SECONDS).until(() -> ...);
            Thread.sleep(2000); 
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 3. 从 MongoDB 查询帖子，看昵称是否变了
        PostDoc updatedPost = postRepository.findById(testPostId).orElseThrow();
        
        System.out.println("MongoDB 中的帖子作者昵称: " + updatedPost.getUserNickname());

        // 4. 断言
        Assertions.assertEquals("新名字_NewName_MQ", updatedPost.getUserNickname(), 
            "MongoDB 中的冗余昵称应该已经被 RabbitMQ 消费者更新了");
            
        System.out.println("✅✅✅ 测试通过！RabbitMQ 链路正常。");
    }
}
