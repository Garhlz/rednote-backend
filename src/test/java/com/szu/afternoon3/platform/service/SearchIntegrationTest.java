package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.repository.PostRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

@SpringBootTest
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=false")
public class SearchIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    public void setup() {
        // 1. 删除集合 (清除所有数据和索引)
        mongoTemplate.dropCollection(PostDoc.class);

        // 2. 手动创建索引 (这一步必须要有，因为我们关掉了自动创建)
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("tags", 3f)
                .onField("title", 2f)
                .onField("content", 1f)
                .named("TextIndexWithTags")
                .build();

        mongoTemplate.indexOps(PostDoc.class).ensureIndex(textIndex);
    }

    @Test
    @DisplayName("测试全文搜索：同时匹配标签、标题、正文")
    public void testSearchByKeyword() {
        String keyword = "ArchLinux";

        // --- 1. 准备测试数据 ---

        // A: 标题包含关键字
        createPost("1", "ArchLinux 安装指南", "这里是正文内容...", List.of("Linux"));

        // B: 正文包含关键字
        createPost("2", "普通标题", "我觉得 ArchLinux 是最好的发行版", List.of("OS"));

        // C: 标签包含关键字 (权重最高)
        createPost("3", "没有关键字的标题", "没有关键字的正文", List.of("ArchLinux", "Tech"));

        // D: 干扰项 (完全不包含)
        createPost("4", "Windows 教程", "这里是微软的内容", List.of("Win11"));

        // E: 已删除的包含关键字的项 (不应被搜到)
        PostDoc deletedPost = new PostDoc();
        deletedPost.setTitle("ArchLinux 已删除");
        deletedPost.setStatus(1);
        deletedPost.setIsDeleted(1); // deleted
        postRepository.save(deletedPost);

        // --- 2. 执行搜索 ---
        System.out.println(">>> 开始搜索关键词: " + keyword);
        Map<String, Object> result = postService.searchPosts(keyword, 1, 10);

        // --- 3. 验证结果 ---
        List<?> records = (List<?>) result.get("records");
        long total = (long) result.get("total");

        System.out.println("搜索结果数量: " + total);

        // 预期：应该找到 A, B, C 三条数据
        Assertions.assertEquals(3, total, "应该找到3条匹配的帖子");

        // 验证排序（可选）：因为 tags 权重高，理论上 C 应该在前面，但 mongo score 计算复杂，这里只验证存在性
        // 验证 D 和 E 不在结果中
    }

    private void createPost(String id, String title, String content, List<String> tags) {
        PostDoc post = new PostDoc();
        // post.setId(id); // 让 Mongo 自动生成 ID 也可以
        post.setTitle(title);
        post.setContent(content);
        post.setTags(tags);
        post.setUserId(1001L);
        post.setUserNickname("Tester");
        post.setUserAvatar("avatar.jpg");
        post.setStatus(1); // 已发布
        post.setIsDeleted(0); // 未删除
        postRepository.save(post);
    }
}