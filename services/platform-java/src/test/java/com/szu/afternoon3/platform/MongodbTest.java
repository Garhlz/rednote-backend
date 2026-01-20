package com.szu.afternoon3.platform;

import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
public class MongodbTest {

    @Autowired
    private PostRepository postRepository;

    @Test
    public void testInsertAndQuery() {
        System.out.println("========== 开始 MongoDB 测试 ==========");

        // 1. 创建一个帖子对象
        PostDoc post = new PostDoc();
        post.setUserId(10086L); // 假设是 ID 为 10086 的用户发的
        post.setUserNickname("小青");
        post.setTitle("这是我的第一篇 MongoDB 笔记");
        post.setContent("Arch Linux + Spring Boot + MongoDB 真好玩！");
        post.setType(0); // 图文
        post.setTags(List.of("Java", "MongoDB", "Arch"));
        post.setCreatedAt(LocalDateTime.now());
        post.setIsDeleted(0); // 别忘了这个字段

        // 2. 保存到 MongoDB
        PostDoc savedPost = postRepository.save(post);
        System.out.println("写入成功！生成的 ID 为: " + savedPost.getId());

        // 3. 尝试查询
        long count = postRepository.count();
        System.out.println("当前 posts 集合总数: " + count);

        System.out.println("========== 测试结束 ==========");
    }
}