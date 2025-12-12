package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.PostTagSuggestDTO;
import com.szu.afternoon3.platform.dto.PostUpdateDTO;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.service.PostService;
import com.szu.afternoon3.platform.service.impl.AiServiceImpl;
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

/**
 * 帖子控制器
 * 处理帖子的发布、浏览、搜索等核心业务
 */
@RestController
@RequestMapping("/api/post")
public class PostController {

    @Autowired
    private PostService postService;
    /**
     * 获取首页帖子流 (推荐/关注)
     */
    @GetMapping("/list")
    @OperationLog(module = "帖子模块", description = "浏览帖子流")
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
     * @param keyword 关键词
     */
    @GetMapping("/search")
    @OperationLog(module = "帖子模块", description = "搜索帖子", bizId = "#keyword")
    public Result<Map<String, Object>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size
    ) {
        Map<String, Object> data = postService.searchPosts(keyword, page, size);
        return Result.success(data);
    }

    /**
     * 获取某用户的帖子列表
     * @param userId 目标用户ID
     */
    @GetMapping("/user/{userId:\\d+}")
    @OperationLog(module = "帖子模块", description = "查看用户帖子", bizId = "#userId")
    public Result<Map<String, Object>> getUserPosts(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Map<String, Object> data = postService.getUserPostList(userId, page, size);
        return Result.success(data);
    }

    /**
     * 获取帖子详情
     * @param postId 帖子ID
     */
    @GetMapping("/{postId}")
    @OperationLog(module = "帖子模块", description = "查看帖子详情", bizId = "#postId")
    public Result<PostVO> getPostDetail(@PathVariable String postId) {
        PostVO vo = postService.getPostDetail(postId);
        return Result.success(vo);
    }

    /**
     * 获取搜索联想词
     */
    @GetMapping("/search/suggest")
    // @OperationLog(module = "帖子模块", description = "搜索联想") // 联想词高频，可不记
    public Result<List<String>> suggestKeywords(@RequestParam String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Result.success(Collections.emptyList());
        }
        List<String> list = postService.getSearchSuggestions(keyword);
        return Result.success(list);
    }

    /**
     * 发布帖子
     * @param dto 帖子内容
     */
    @PostMapping
    @OperationLog(module = "帖子模块", description = "发布帖子", bizId = "#dto.title")
    public Result<Map<String, String>> createPost(@RequestBody @Valid PostCreateDTO dto) {
        String postId = postService.createPost(dto);
        Map<String, String> data = new HashMap<>();
        data.put("id", postId);
        return Result.success(data);
    }

    /**
     * 删除帖子
     * @param postId 帖子ID
     */
    @DeleteMapping("/{id}")
    @OperationLog(module = "帖子模块", description = "删除帖子", bizId = "#postId")
    public Result<Void> deletePost(@PathVariable("id") String postId) {
        postService.deletePost(postId);
        return Result.success();
    }

    /**
     * 修改帖子
     * @param postId 帖子ID
     */
    @PutMapping("/{id}")
    @OperationLog(module = "帖子模块", description = "修改帖子", bizId = "#postId")
    public Result<Void> updatePost(
            @PathVariable("id") String postId,
            @RequestBody PostUpdateDTO dto
    ) {
        postService.updatePost(postId, dto);
        return Result.success();
    }
}