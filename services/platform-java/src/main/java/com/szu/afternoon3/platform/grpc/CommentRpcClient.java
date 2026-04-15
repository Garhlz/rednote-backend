package com.szu.afternoon3.platform.grpc;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.CommentCreateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.vo.CommentVO;
import com.szu.afternoon3.platform.vo.SimpleUserVO;
import com.szu.afternoon3.platform.vo.UserInfo;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommentRpcClient {

    @GrpcClient("comment-service")
    private comment.CommentServiceGrpc.CommentServiceBlockingStub commentStub;

    @Autowired
    private UserMapper userMapper;

    public CommentVO createComment(CommentCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        User user = currentUserId == null ? null : userMapper.selectById(currentUserId);

        comment.CommentOuterClass.Comment response = commentStub.createComment(
                comment.CommentOuterClass.CreateCommentRequest.newBuilder()
                        .setPostId(dto.getPostId())
                        .setCurrentUserId(currentUserId == null ? 0L : currentUserId)
                        .setUserNickname(resolveNickname(user))
                        .setUserAvatar(resolveAvatar(user))
                        .setContent(dto.getContent() == null ? "" : dto.getContent())
                        .setParentId(dto.getParentId() == null ? "" : dto.getParentId())
                        .build()
        );

        return toVO(response);
    }

    private String resolveNickname(User user) {
        if (user != null && user.getNickname() != null) {
            return user.getNickname();
        }
        return UserContext.getNickname() == null ? "" : UserContext.getNickname();
    }

    private String resolveAvatar(User user) {
        if (user != null && user.getAvatar() != null) {
            return user.getAvatar();
        }
        return "";
    }

    private CommentVO toVO(comment.CommentOuterClass.Comment item) {
        CommentVO vo = new CommentVO();
        vo.setId(item.getId());
        vo.setContent(item.getContent());
        vo.setCreatedAt(item.getCreatedAt() > 0 ? formatEpoch(item.getCreatedAt()) : null);
        vo.setLikeCount(item.getLikeCount());
        vo.setIsLiked(item.getIsLiked());
        vo.setReplyCount(item.getReplyCount());

        SimpleUserVO author = new SimpleUserVO();
        author.setUserId(String.valueOf(item.getUserId()));
        author.setNickname(item.getUserNickname());
        author.setAvatar(item.getUserAvatar());
        vo.setAuthor(author);

        if (item.getReplyToUserId() > 0) {
            UserInfo replyTo = new UserInfo();
            replyTo.setUserId(String.valueOf(item.getReplyToUserId()));
            replyTo.setNickname(item.getReplyToUserNickname());
            vo.setReplyToUser(replyTo);
        }

        return vo;
    }

    private String formatEpoch(long seconds) {
        return java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(seconds),
                java.time.ZoneId.systemDefault()
        ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
