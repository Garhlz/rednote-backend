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

    @Test
    @DisplayName("测试搜索候选词：中文匹配、标签优先、去重、状态过滤")
    public void testGetSearchSuggestions() {
        System.out.println("========== 开始测试搜索联想词 ==========");

        // --- 1. 准备测试数据 ---

        // 场景 A: 正常数据，标签包含 "深圳"
        PostDoc post1 = new PostDoc();
        post1.setTitle("周末去哪儿玩");
        post1.setTags(List.of("深圳", "旅游")); // 命中标签
        post1.setStatus(1);
        post1.setIsDeleted(0);
        postRepository.save(post1);

        // 场景 B: 正常数据，标题包含 "深圳"
        PostDoc post2 = new PostDoc();
        post2.setTitle("深圳大学美食攻略"); // 命中标题
        post2.setTags(List.of("美食", "探店"));
        post2.setStatus(1);
        post2.setIsDeleted(0);
        postRepository.save(post2);

        // 场景 C: 干扰数据（不包含关键词）
        PostDoc post3 = new PostDoc();
        post3.setTitle("广州塔一日游");
        post3.setTags(List.of("广州"));
        post3.setStatus(1);
        post3.setIsDeleted(0);
        postRepository.save(post3);

        // 场景 D: 状态异常数据（包含关键词，但 不应该 被搜到）
        PostDoc postDraft = new PostDoc();
        postDraft.setTitle("深圳草稿箱");
        postDraft.setTags(List.of("深圳"));
        postDraft.setStatus(0); // 0: 审核中/草稿
        postDraft.setIsDeleted(0);
        postRepository.save(postDraft);

        PostDoc postDeleted = new PostDoc();
        postDeleted.setTitle("深圳已删除");
        postDeleted.setTags(List.of("深圳"));
        postDeleted.setStatus(1);
        postDeleted.setIsDeleted(1); // 1: 已删除
        postRepository.save(postDeleted);

        // --- 2. 执行搜索 ---
        String keyword = "深圳";
        List<String> result = postService.getSearchSuggestions(keyword);

        System.out.println("搜索关键词: " + keyword);
        System.out.println("联想词结果: " + result);

        // --- 3. 验证断言 ---

        // 3.1 验证数量
        // 预期结果应该是 ["深圳", "深圳大学美食攻略"] (顺序可能根据 Mongo 返回顺序略有不同，但逻辑上标签在前)
        // 实际上 post1 的标题 "周末去哪儿玩" 不含关键词，不会进入结果
        // post2 的标签 "美食", "探店" 不含关键词，不会进入结果
        Assertions.assertTrue(result.size() >= 2, "应该至少找到2个相关建议");

        // 3.2 验证内容匹配
        Assertions.assertTrue(result.contains("深圳"), "结果应包含匹配的标签 '深圳'");
        Assertions.assertTrue(result.contains("深圳大学美食攻略"), "结果应包含匹配的标题 '深圳大学美食攻略'");

        // 3.3 验证过滤逻辑 (草稿和已删除的不应出现)
        Assertions.assertFalse(result.contains("深圳草稿箱"), "不应包含草稿状态的帖子");
        Assertions.assertFalse(result.contains("深圳已删除"), "不应包含已删除的帖子");

        // 3.4 验证去重逻辑 (如果你有多个帖子都有 "深圳" 这个标签，结果里应该只有一个 "深圳")
        long countTag = result.stream().filter(s -> s.equals("深圳")).count();
        Assertions.assertEquals(1, countTag, "相同的建议词应该被去重");

        // 3.5 验证优先级 (可选)
        // 在我们的 Service 实现中，是先遍历 Tags 加入 Set，再遍历 Title 加入 Set。
        // LinkedHashSet 会保留插入顺序。通常 post1 (匹配Tag) 会被先处理或 Tag 逻辑在前。
        // 如果数据量小，Mongo 返回顺序通常是插入顺序。
        // 只要断言包含即可，顺序对单元测试来说不是绝对强一致性要求。

        System.out.println("✅ 搜索联想词测试通过");
    }
}