package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.CommentCreateDTO;
import com.szu.afternoon3.platform.service.CommentService;
import com.szu.afternoon3.platform.vo.CommentCreateVO;
import com.szu.afternoon3.platform.vo.CommentVO;
import com.szu.afternoon3.platform.vo.PageResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评论控制器
 * 处理评论的发布、删除及列表查询
 */
@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 发表评论
     * @param dto 评论内容
     */
    @PostMapping
    @OperationLog(module = "评论模块", description = "发布评论", bizId = "#dto.postId")
    public Result<CommentCreateVO> createComment(@RequestBody @Valid CommentCreateDTO dto) {
        return Result.success(commentService.createComment(dto));
    }

    /**
     * 删除评论
     * @param commentId 评论ID
     */
    @DeleteMapping("/{id}")
    @OperationLog(module = "评论模块", description = "删除评论", bizId = "#commentId")
    public Result<Void> deleteComment(@PathVariable("id") String commentId) {
        commentService.deleteComment(commentId);
        return Result.success();
    }

    /**
     * 获取一级评论列表
     * @param postId 帖子ID
     */
    @GetMapping("/list")
    @OperationLog(module = "评论模块", description = "获取一级评论", bizId = "#postId")
    public Result<PageResult<CommentVO>>getRootComments(
            @RequestParam String postId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        return Result.success(commentService.getRootComments(postId, page, size));
    }

    /**
     * 获取子评论列表 (展开回复)
     * @param rootId 一级评论ID
     */
    @GetMapping("/sub-list")
    @OperationLog(module = "评论模块", description = "获取子评论", bizId = "#rootId")
    public Result<PageResult<CommentVO>> getSubComments(
            @RequestParam String rootId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        return Result.success(commentService.getSubComments(rootId, page, size));
    }
}
