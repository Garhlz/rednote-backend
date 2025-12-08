package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.PostRateDTO;
import com.szu.afternoon3.platform.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/interaction")
public class InteractionController {

    @Autowired
    private InteractionService interactionService;

    // --- 点赞 ---
    @PostMapping("/like/post")
    public Result<Void> likePost(@RequestBody Map<String, String> params) {
        interactionService.likePost(params.get("postId"));
        return Result.success();
    }

    // --- 取消点赞 ---
    @PostMapping("/unlike/post")
    public Result<Void> unlikePost(@RequestBody Map<String, String> params) {
        interactionService.unlikePost(params.get("postId"));
        return Result.success();
    }

    // --- 收藏 ---
    @PostMapping("/collect/post")
    public Result<Void> collectPost(@RequestBody Map<String, String> params) {
        interactionService.collectPost(params.get("postId"));
        return Result.success();
    }

    @PostMapping("/uncollect/post")
    public Result<Void> uncollectPost(@RequestBody Map<String, String> params) {
        interactionService.uncollectPost(params.get("postId"));
        return Result.success();
    }

    // --- 评分 ---
    @PostMapping("/rate/post")
    public Result<Void> ratePost(@RequestBody @Valid PostRateDTO dto) {
        interactionService.ratePost(dto);
        return Result.success();
    }

    // --- 评论点赞 (预留接口) ---
    @PostMapping("/like/comment")
    public Result<Void> likeComment(@RequestBody Map<String, String> params) {
        interactionService.likeComment(params.get("commentId"));
        return Result.success();
    }

    @PostMapping("/unlike/comment")
    public Result<Void> unlikeComment(@RequestBody Map<String, String> params) {
        interactionService.unlikeComment(params.get("commentId"));
        return Result.success();
    }
}