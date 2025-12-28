package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.CommentCreateDTO;
import com.szu.afternoon3.platform.vo.CommentVO;
import com.szu.afternoon3.platform.vo.PageResult;

import java.util.Map;

public interface CommentService {
    // 发表评论
    void createComment(CommentCreateDTO dto);
    
    // 删除评论
    void deleteComment(String commentId);
    
    // 获取帖子的一级评论列表 (包含子评论预览)
    PageResult<CommentVO> getRootComments(String postId, Integer page, Integer size);
    
    // 获取某条评论的子回复列表 (点击展开时调用)
    PageResult<CommentVO> getSubComments(String rootCommentId, Integer page, Integer size);
}