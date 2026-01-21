package com.szu.afternoon3.platform.service.impl;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.PostRateDTO;
import com.szu.afternoon3.platform.service.InteractionService;
// 导入生成的 gRPC 类
import interaction.Interaction.InteractionRequest;
import interaction.Interaction.RateRequest;
import interaction.InteractionServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    private final InteractionServiceGrpc.InteractionServiceBlockingStub interactionStub;

    // 使用构造器注入
    public InteractionServiceImpl(@GrpcClient("interaction-service") InteractionServiceGrpc.InteractionServiceBlockingStub interactionStub) {
        this.interactionStub = interactionStub;
        // 如果这里打印出来了，说明注入成功了
        log.info(">>>>>> gRPC Stub Initialized: {}", interactionStub);
    }

    @Override
    public void likePost(String postId) {
        interactionStub.likePost(buildRequest(postId));
    }

    @Override
    public void unlikePost(String postId) {
        interactionStub.unlikePost(buildRequest(postId));
    }

    @Override
    public void collectPost(String postId) {
        interactionStub.collectPost(buildRequest(postId));
    }

    @Override
    public void uncollectPost(String postId) {
        interactionStub.uncollectPost(buildRequest(postId));
    }

    @Override
    public void ratePost(PostRateDTO dto) {
        RateRequest request = RateRequest.newBuilder()
                .setUserId(UserContext.getUserId())
                .setTargetId(dto.getPostId())
                .setScore(dto.getScore())
                .build();
        interactionStub.ratePost(request);
    }

    @Override
    public void likeComment(String commentId) {
        interactionStub.likeComment(buildRequest(commentId));
    }

    @Override
    public void unlikeComment(String commentId) {
        interactionStub.unlikeComment(buildRequest(commentId));
    }

    private InteractionRequest buildRequest(String targetId) {
        return InteractionRequest.newBuilder()
                .setUserId(UserContext.getUserId())
                .setTargetId(targetId)
                .build();
    }
}