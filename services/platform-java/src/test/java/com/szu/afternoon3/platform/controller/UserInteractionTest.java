package com.szu.afternoon3.platform.controller;
import org.junit.jupiter.api.Disabled;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.szu.afternoon3.platform.dto.PostCreateDTO;
import com.szu.afternoon3.platform.dto.PostRateDTO;
import com.szu.afternoon3.platform.dto.WechatLoginDTO;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.util.WeChatUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 保证 SQL 数据自动回滚
@Disabled("migrated to gateway/user-rpc")
public class UserInteractionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PostRepository postRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @MockBean private WeChatUtil weChatUtil; // Mock 微信登录

    private String token; // 当前登录用户的 Token
    private String postId; // 测试用的帖子 ID

    @BeforeEach
    public void setup() throws Exception {
        // 1. 清理 Mongo 数据 (SQL 会由 @Transactional 处理)
        mongoTemplate.dropCollection(PostDoc.class);
        // 清理关系表 (如果你有单独的 Collection 类，建议也清理，或者由 dropDatabase 替代)
        mongoTemplate.dropCollection("post_likes");
        mongoTemplate.dropCollection("post_collects");
        mongoTemplate.dropCollection("post_ratings");
        mongoTemplate.dropCollection("post_view_histories");

        // 2. 模拟登录获取 Token
        String openid = "test_interaction_openid_" + System.currentTimeMillis();
        Mockito.when(weChatUtil.getOpenId(Mockito.anyString())).thenReturn(openid);

        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setCode("mock_code");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(loginDTO)))
                .andExpect(status().isOk())
                .andReturn();

        String respStr = loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        this.token = JSONUtil.parseObj(respStr).getByPath("data.token", String.class);

        // 3. 发布一个测试帖子
        PostCreateDTO postDTO = new PostCreateDTO();
        postDTO.setTitle("测试互动帖子");
        postDTO.setContent("测试内容");
        postDTO.setType(0); // 图文
        postDTO.setImages(List.of("http://img.com/1.jpg"));
        postDTO.setTags(List.of("测试"));

        MvcResult postResult = mockMvc.perform(post("/api/post")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(postDTO)))
                .andExpect(status().isOk())
                .andReturn();

        String postResp = postResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        this.postId = JSONUtil.parseObj(postResp).getByPath("data.id", String.class);

        // 确保状态为已发布 (1)，否则有些列表查不到
        PostDoc doc = postRepository.findById(postId).orElseThrow();
        doc.setStatus(1);
        postRepository.save(doc);
    }

    @AfterEach
    public void tearDown() {
        // 再次清理 Mongo，保持环境整洁
        mongoTemplate.dropCollection(PostDoc.class);
    }

    @Test
    @DisplayName("测试：我的帖子列表")
    public void testMyPosts() throws Exception {
        mockMvc.perform(get("/api/user/posts")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "0")) // 筛选图文
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(postId))
                .andExpect(jsonPath("$.data.records[0].title").value("测试互动帖子"));
    }

    @Test
    @DisplayName("测试：点赞与获取点赞列表")
    public void testLikeFlow() throws Exception {
        // 1. 点赞
        JSONObject body = new JSONObject().set("postId", postId);
        mockMvc.perform(post("/api/interaction/like/post")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk());
        Thread.sleep(1000);
        // 2. 获取列表
        mockMvc.perform(get("/api/user/likes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(postId));
    }

    @Test
    @DisplayName("测试：收藏与获取收藏列表")
    public void testCollectFlow() throws Exception {
        // 1. 收藏
        JSONObject body = new JSONObject().set("postId", postId);
        mockMvc.perform(post("/api/interaction/collect/post")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk());
        Thread.sleep(1000);
        // 2. 获取列表
        mockMvc.perform(get("/api/user/collects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(postId));
    }

    @Test
    @DisplayName("测试：评分与获取评分列表")
    public void testRateFlow() throws Exception {
        // 1. 评分 (4.5分)
        PostRateDTO rateDTO = new PostRateDTO();
        rateDTO.setPostId(postId);
        rateDTO.setScore(4.5);

        mockMvc.perform(post("/api/interaction/rate/post")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(rateDTO)))
                .andExpect(status().isOk());
        Thread.sleep(1000);
        // 2. 获取列表 (验证 myScore 字段)
        mockMvc.perform(get("/api/user/ratings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(postId))
                .andExpect(jsonPath("$.data.records[0].myScore").value(4.5));
    }

    @Test
    @DisplayName("测试：浏览历史记录")
    public void testHistoryFlow() throws Exception {
        // 1. 调用详情接口 (触发浏览记录)
        mockMvc.perform(get("/api/post/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 【关键】因为记录历史是 @Async 异步的，这里必须等待一小会儿
        Thread.sleep(1000);

        // 2. 获取历史列表
        mockMvc.perform(get("/api/user/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(postId));
    }
}