package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.dto.CommentCreateDTO;
import com.szu.afternoon3.platform.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 发表评论
     * 对应接口: POST /api/comment
     */
    @PostMapping
    public Result<Void> createComment(@RequestBody @Valid CommentCreateDTO dto) {
        commentService.createComment(dto);
        return Result.success();
    }

    /**
     * 删除评论
     * 对应接口: DELETE /api/comment/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable("id") String commentId) {
        commentService.deleteComment(commentId);
        return Result.success();
    }

    /**
     * 获取一级评论列表 (包含前3条子评论预览)
     * 对应接口: GET /api/comment/list
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getRootComments(
            @RequestParam String postId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        
        return Result.success(commentService.getRootComments(postId, page, size));
    }

    /**
     * 【新增】获取某条一级评论下的子评论列表 (点击"展开回复"时调用)
     * 对应接口: GET /api/comment/sub-list
     */
    @GetMapping("/sub-list")
    public Result<Map<String, Object>> getSubComments(
            @RequestParam String rootId, // 即 parentId
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        
        return Result.success(commentService.getSubComments(rootId, page, size));
    }
}