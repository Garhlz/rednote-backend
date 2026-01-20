package com.szu.afternoon3.platform;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.UserProfileUpdateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.CommentRepository;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.UserFollowRepository;
import com.szu.afternoon3.platform.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
public class DataConsistencyTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private UserFollowRepository userFollowRepository;

    @Test
    @DisplayName("测试修改个人资料后，MongoDB数据是否同步更新")
    public void testDataConsistency() throws InterruptedException {
        // --- 1. 准备环境 ---
        // 1.1 创建一个测试用户 (Postgres)
        User user = new User();
        user.setNickname("旧名字_Old");
        user.setAvatar("http://old_avatar.jpg");
        user.setEmail("test_consistency@szu.edu.cn");
        user.setStatus(1);
        userMapper.insert(user);
        Long userId = user.getId();
        System.out.println("创建测试用户 ID: " + userId);

        // 模拟登录
        UserContext.setUserId(userId);

        try {
            // 1.2 在 MongoDB 里造一些相关的旧数据
            // (A) 帖子
            PostDoc post = new PostDoc();
            post.setUserId(userId);
            post.setUserNickname("旧名字_Old"); // 冗余字段
            post.setUserAvatar("http://old_avatar.jpg"); // 冗余字段
            post.setTitle("测试贴");
            post.setTags(List.of("test"));
            postRepository.save(post);

            // (B) 评论 (我是回复者)
            CommentDoc comment = new CommentDoc();
            comment.setUserId(userId);
            comment.setUserNickname("旧名字_Old");
            comment.setUserAvatar("http://old_avatar.jpg");
            comment.setContent("测试评论");
            comment.setPostId(post.getId());
            commentRepository.save(comment);

            // (C) 关注 (我关注了别人)
            UserFollowDoc follow = new UserFollowDoc();
            follow.setUserId(userId); // 我是发起者
            follow.setUserNickname("旧名字_Old");
            follow.setTargetUserId(99999L); // 随便一个ID
            userFollowRepository.save(follow);

            // --- 2. 执行动作：修改资料 ---
            UserProfileUpdateDTO updateDTO = new UserProfileUpdateDTO();
            updateDTO.setNickname("新名字_New");
            updateDTO.setAvatar("http://new_avatar.png");

            System.out.println(">>> 开始执行更新...");
            userService.updateProfile(updateDTO);

            // --- 3. 等待异步同步 ---
            // 因为 Listener 是 @Async 的，我们需要稍微等一下
            Thread.sleep(2000);

            // --- 4. 验证结果 ---
            System.out.println(">>> 开始验证 MongoDB 数据...");

            // 验证帖子
            PostDoc updatedPost = postRepository.findById(post.getId()).orElseThrow();
            Assertions.assertEquals("新名字_New", updatedPost.getUserNickname(), "帖子里的昵称未同步！");
            Assertions.assertEquals("http://new_avatar.png", updatedPost.getUserAvatar(), "帖子里的头像未同步！");

            // 验证评论
            CommentDoc updatedComment = commentRepository.findById(comment.getId()).orElseThrow();
            Assertions.assertEquals("新名字_New", updatedComment.getUserNickname(), "评论里的昵称未同步！");

            // 验证关注
            UserFollowDoc updatedFollow = userFollowRepository.findById(follow.getId()).orElseThrow();
            Assertions.assertEquals("新名字_New", updatedFollow.getUserNickname(), "关注表里的昵称未同步！");

            System.out.println("✅✅✅ 数据一致性测试通过！Mongo 数据已成功同步。");

        } finally {
            // 清理数据
            UserContext.clear();
            // 实际项目中测试库会自动回滚，或者这里手动清理
            postRepository.deleteAll();
            commentRepository.deleteAll();
            userFollowRepository.deleteAll();
            userMapper.deleteById(userId);
        }
    }
}