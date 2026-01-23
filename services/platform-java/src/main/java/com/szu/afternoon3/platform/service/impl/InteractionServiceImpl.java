package com.szu.afternoon3.platform.service.impl;

import com.szu.afternoon3.platform.dto.PostRateDTO;
import com.szu.afternoon3.platform.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InteractionServiceImpl implements InteractionService {
    @Override
    public void likePost(String postId) {
        log.warn("interaction grpc disabled, likePost noop for postId={}", postId);
    }

    @Override
    public void unlikePost(String postId) {
        log.warn("interaction grpc disabled, unlikePost noop for postId={}", postId);
    }

    @Override
    public void collectPost(String postId) {
        log.warn("interaction grpc disabled, collectPost noop for postId={}", postId);
    }

    @Override
    public void uncollectPost(String postId) {
        log.warn("interaction grpc disabled, uncollectPost noop for postId={}", postId);
    }

    @Override
    public void ratePost(PostRateDTO dto) {
        log.warn("interaction grpc disabled, ratePost noop for postId={}", dto == null ? null : dto.getPostId());
    }

    @Override
    public void likeComment(String commentId) {
        log.warn("interaction grpc disabled, likeComment noop for commentId={}", commentId);
    }

    @Override
    public void unlikeComment(String commentId) {
        log.warn("interaction grpc disabled, unlikeComment noop for commentId={}", commentId);
    }
}
