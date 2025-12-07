package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.PostService;
import com.szu.afternoon3.platform.vo.PostVO;
import com.szu.afternoon3.platform.dto.PostCreateDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/post")
public class PostController {

    @Autowired
    private PostService postService;

    /**
     * 获取首页帖子流
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getPostList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "recommend") String tab,
            @RequestParam(required = false) String tag
    ) {
        Map<String, Object> data = postService.getPostList(page, size, tab, tag);
        return Result.success(data);
    }

    /**
     * 搜索帖子
     * 对应 Apifox 接口: /api/post/search (GET)
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        Map<String, Object> data = postService.searchPosts(keyword, page, size);
        return Result.success(data);
    }

    /**
     * 获取用户的帖子
     * 正则表达式 {userId:\\d+} 确保只有纯数字ID才会进入此方法。
     */
    @GetMapping("/user/{userId:\\d+}")
    public Result<Map<String, Object>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Map<String, Object> data = postService.getUserPostList(userId, page, size);
        return Result.success(data);
    }

    /**
     * 获取帖子详情
     */
    @GetMapping("/{postId}")
    public Result<PostVO> getPostDetail(@PathVariable String postId) {
        PostVO vo = postService.getPostDetail(postId);
        return Result.success(vo);
    }

    /**
     * 搜索候选词/联想词
     * 场景：用户在搜索框输入 "深" -> 提示 "深圳大学", "深圳美食"
     */
    @GetMapping("/search/suggest")
    public Result<List<String>> suggestKeywords(@RequestParam String keyword) {
        // 1. 判空
        if (StrUtil.isBlank(keyword)) {
            return Result.success(Collections.emptyList());
        }
        // 2. 调用服务
        List<String> list = postService.getSearchSuggestions(keyword);
        return Result.success(list);
    }

    /**
     * 发布帖子
     * 对应 Apifox 接口: /api/post (POST)
     */
    @PostMapping
    public Result<Map<String, String>> createPost(@RequestBody @Valid PostCreateDTO dto) {
        String postId = postService.createPost(dto);

        Map<String, String> data = new HashMap<>();
        data.put("id", postId);

        return Result.success(data);
    }

    /**
     * 删除帖子
     * 对应 Apifox 接口: /api/post/{id} (DELETE)
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable("id") String postId) {
        postService.deletePost(postId);
        return Result.success();
    }
}