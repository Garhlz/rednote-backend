package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.CommentIdDTO;
import com.szu.afternoon3.platform.dto.PostIdDTO;
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

    // --- 帖子相关 (复用 PostIdDTO) ---

    @PostMapping("/like/post")
    @OperationLog(module = "互动模块", description = "点赞帖子", bizId = "#dto.postId") // 注意这里变成 dto.postId
    public Result<Void> likePost(@RequestBody @Valid PostIdDTO dto) {
        interactionService.likePost(dto.getPostId());
        return Result.success();
    }

    @PostMapping("/unlike/post")
    @OperationLog(module = "互动模块", description = "取消点赞帖子", bizId = "#dto.postId")
    public Result<Void> unlikePost(@RequestBody @Valid PostIdDTO dto) {
        interactionService.unlikePost(dto.getPostId());
        return Result.success();
    }

    @PostMapping("/collect/post")
    @OperationLog(module = "互动模块", description = "收藏帖子", bizId = "#dto.postId")
    public Result<Void> collectPost(@RequestBody @Valid PostIdDTO dto) {
        interactionService.collectPost(dto.getPostId());
        return Result.success();
    }

    @PostMapping("/uncollect/post")
    @OperationLog(module = "互动模块", description = "取消收藏帖子", bizId = "#dto.postId")
    public Result<Void> uncollectPost(@RequestBody @Valid PostIdDTO dto) {
        interactionService.uncollectPost(dto.getPostId());
        return Result.success();
    }

    // --- 评论相关 (复用 CommentIdDTO) ---

    @PostMapping("/like/comment")
    @OperationLog(module = "互动模块", description = "点赞评论", bizId = "#dto.commentId")
    public Result<Void> likeComment(@RequestBody @Valid CommentIdDTO dto) {
        interactionService.likeComment(dto.getCommentId());
        return Result.success();
    }

    @PostMapping("/unlike/comment")
    @OperationLog(module = "互动模块", description = "取消点赞评论", bizId = "#dto.commentId")
    public Result<Void> unlikeComment(@RequestBody @Valid CommentIdDTO dto) {
        interactionService.unlikeComment(dto.getCommentId());
        return Result.success();
    }

    // --- 评分保持原样 (它已经有专门的DTO了) ---
    @PostMapping("/rate/post")
    @OperationLog(module = "互动模块", description = "评分帖子", bizId = "#dto.postId")
    public Result<Void> ratePost(@RequestBody @Valid PostRateDTO dto) {
        interactionService.ratePost(dto);
        return Result.success();
    }
}