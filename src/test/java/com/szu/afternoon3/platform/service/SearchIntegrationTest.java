package com.szu.afternoon3.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.afternoon3.platform.dto.PostCreateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.util.SearchHelper;
import com.szu.afternoon3.platform.common.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
//@Transactional
public class SearchIntegrationTest {

    @Autowired private SearchHelper searchHelper;
    @Autowired private PostService postService;
    @Autowired private PostRepository postRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserMapper userMapper;

    private Long userId;
    private String userEmail;
    @BeforeEach
    public void setup() {
        // 1. 清理环境 (手动清理，因为没了 Transactional)
        cleanUp();

        // 2. 强制重建索引 (这是最核心的一步)
        mongoTemplate.dropCollection(PostDoc.class); // 删集合=删索引

        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("searchTerms", 5f)
                .named("PostDoc_TextIndex")
                .build();
        mongoTemplate.indexOps(PostDoc.class).ensureIndex(textIndex);

        // 3. 准备用户
        User user = new User();
        user.setNickname("搜索测试员");
        this.userEmail = "search_test_" + System.currentTimeMillis() + "@szu.edu.cn";
        user.setEmail(userEmail);
        user.setStatus(1);
        userMapper.insert(user);
        this.userId = user.getId();
        UserContext.setUserId(userId);
    }
    @AfterEach
    public void tearDown() {
        // 测试结束后清理垃圾数据
        cleanUp();
    }
    private void cleanUp() {
        // 清理 Mongo
        postRepository.deleteAll();

        // 清理 Postgres (手动删，防止 Unique Key 冲突)
        if (userEmail != null) {
            userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getEmail, userEmail));
        }
    }

    @Test
    @DisplayName("Debug: 查看 Jieba 分词结果")
    public void debugJiebaTokenization() {
        String title = "深圳大学的饭堂很好吃";
        String content = "今天去了南区，发现荔园美食荟有很多新档口。ArchLinux安装也很快。";
        List<String> tags = List.of("美食探店", "深圳大学");

        List<String> terms = searchHelper.generateSearchTerms(title, content, tags);

        System.out.println("Jieba 分词结果: " + terms);

        // 验证分词 (INDEX模式应该能把 '深圳' 切出来)
        Assertions.assertTrue(terms.contains("深圳"), "应该包含 '深圳'");
        Assertions.assertTrue(terms.contains("大学"), "应该包含 '大学'");

        // 【关键修复 3】: 断言改为小写，匹配分词器的行为
        Assertions.assertTrue(terms.contains("archlinux"), "应该包含 'archlinux' (小写)");
    }

    @Test
    @DisplayName("验证创建帖子时 searchTerms 是否正确存入 DB")
    public void testSaveSearchTerms() {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle("Java并发编程");
        dto.setContent("学习多线程和锁机制");
        dto.setType(2);
        dto.setImages(List.of("http://dummy.jpg"));
        dto.setTags(List.of("Java", "编程"));

        String postId = postService.createPost(dto);

        PostDoc savedPost = postRepository.findById(postId).orElseThrow();
        List<String> dbTerms = savedPost.getSearchTerms();

        System.out.println("数据库中的 searchTerms: " + dbTerms);

        Assertions.assertNotNull(dbTerms);
        // 【关键修复 2】: 接受 '多线程' 这个词 (Jieba 可能不拆分它)
        boolean hasThread = dbTerms.contains("线程") || dbTerms.contains("多线程");
        Assertions.assertTrue(hasThread, "应该包含 '线程' 或 '多线程'");
    }

    @Test
    @DisplayName("测试全文搜索召回能力")
    public void testFullSearch() throws InterruptedException { // 允许抛出中断异常
        // A: 包含 "深圳"
        createPost("深圳周末去哪儿", "可以在南山逛街", List.of("旅游"));

        // B: 包含 "ArchLinux"
        createPost("我的 ArchLinux 安装笔记", "sudo pacman -Syu", List.of("Linux", "OS"));

        // C: 干扰项
        createPost("广州早茶推荐", "凤爪排骨", List.of("美食"));

        // 【关键】给 MongoDB 一点时间建索引 (500ms - 1s)
        // 在真实生产环境不需要，但测试环境瞬间写入瞬间查容易出问题
//        Thread.sleep(1000);

        // 1. 中文搜索
        System.out.println(">>> 搜索: '深圳逛街'");
        Map<String, Object> resultA = postService.searchPosts("深圳逛街", 1, 10);
        List<?> listA = (List<?>) resultA.get("records");

        // 打印一下到底搜到了啥，方便调试
        System.out.println("搜索结果 A: " + listA);

        Assertions.assertEquals(1, listA.size(), "应该找到 1 条关于深圳的帖子");

        // 2. 英文搜索
        System.out.println(">>> 搜索: 'pacman'");
        Map<String, Object> resultB = postService.searchPosts("pacman", 1, 10);
        List<?> listB = (List<?>) resultB.get("records");
        Assertions.assertEquals(1, listB.size(), "应该找到 ArchLinux 的帖子");
    }

    private void createPost(String title, String content, List<String> tags) {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setType(2);
        dto.setImages(List.of("http://dummy.jpg"));
        dto.setTags(tags);

        // 1. 调用 Service 创建 (此时状态是 0)
        String postId = postService.createPost(dto);

        // 2. 【关键修复】手动把状态改为 1 (已发布)，否则搜不到！
        PostDoc post = postRepository.findById(postId).orElseThrow();
        post.setStatus(1);
        postRepository.save(post);
    }

    @Test
    @DisplayName("手动洗数据：为旧帖子生成分词索引")
    // @Rollback(false) // 如果需要在真实库生效，需要配合事务提交配置，或者直接在 Controller 写个临时接口
    public void migrateOldData() {
        // 1. 查出所有没有 searchTerms 的帖子
        Query query = new Query(Criteria.where("searchTerms").exists(false));
        List<PostDoc> oldPosts = mongoTemplate.find(query, PostDoc.class);

        System.out.println("扫描到旧帖子数量: " + oldPosts.size());

        for (PostDoc post : oldPosts) {
            // 2. 重新生成分词
            List<String> terms = searchHelper.generateSearchTerms(
                    post.getTitle(),
                    post.getContent(),
                    post.getTags()
            );
            post.setSearchTerms(terms);

            // 3. 保存
            postRepository.save(post);
        }
        System.out.println("数据清洗完成！");
    }

}