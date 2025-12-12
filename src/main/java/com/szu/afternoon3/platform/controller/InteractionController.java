package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.annotation.OperationLog;
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

/**
 * 互动控制器
 * 处理点赞、收藏、评分等交互逻辑
 */
@RestController
@RequestMapping("/api/interaction")
public class InteractionController {

    @Autowired
    private InteractionService interactionService;

    /**
     * 点赞帖子
     */
    @PostMapping("/like/post")
    @OperationLog(module = "互动模块", description = "点赞帖子", bizId = "#params['postId']")
    public Result<Void> likePost(@RequestBody Map<String, String> params) {
        interactionService.likePost(params.get("postId"));
        return Result.success();
    }

    /**
     * 取消点赞帖子
     */
    @PostMapping("/unlike/post")
    @OperationLog(module = "互动模块", description = "取消点赞帖子", bizId = "#params['postId']")
    public Result<Void> unlikePost(@RequestBody Map<String, String> params) {
        interactionService.unlikePost(params.get("postId"));
        return Result.success();
    }

    /**
     * 收藏帖子
     */
    @PostMapping("/collect/post")
    @OperationLog(module = "互动模块", description = "收藏帖子", bizId = "#params['postId']")
    public Result<Void> collectPost(@RequestBody Map<String, String> params) {
        interactionService.collectPost(params.get("postId"));
        return Result.success();
    }

    /**
     * 取消收藏帖子
     */
    @PostMapping("/uncollect/post")
    @OperationLog(module = "互动模块", description = "取消收藏帖子", bizId = "#params['postId']")
    public Result<Void> uncollectPost(@RequestBody Map<String, String> params) {
        interactionService.uncollectPost(params.get("postId"));
        return Result.success();
    }

    /**
     * 帖子评分
     */
    @PostMapping("/rate/post")
    @OperationLog(module = "互动模块", description = "评分帖子", bizId = "#dto.postId")
    public Result<Void> ratePost(@RequestBody @Valid PostRateDTO dto) {
        interactionService.ratePost(dto);
        return Result.success();
    }

    /**
     * 点赞评论
     */
    @PostMapping("/like/comment")
    @OperationLog(module = "互动模块", description = "点赞评论", bizId = "#params['commentId']")
    public Result<Void> likeComment(@RequestBody Map<String, String> params) {
        interactionService.likeComment(params.get("commentId"));
        return Result.success();
    }

    /**
     * 取消点赞评论
     */
    @PostMapping("/unlike/comment")
    @OperationLog(module = "互动模块", description = "取消点赞评论", bizId = "#params['commentId']")
    public Result<Void> unlikeComment(@RequestBody Map<String, String> params) {
        interactionService.unlikeComment(params.get("commentId"));
        return Result.success();
    }
}