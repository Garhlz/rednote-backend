package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.dto.PostRateDTO;

public interface InteractionService {
    void likePost(String postId);
    void unlikePost(String postId);

    void collectPost(String postId);
    void uncollectPost(String postId);

    void ratePost(PostRateDTO dto);

    // 评论点赞预留
    void likeComment(String commentId);
    void unlikeComment(String commentId);
}