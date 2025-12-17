package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostAuditPassEvent {
    // 这其实和你修改后的 PostCreateEvent 结构基本一致
    // 标志着：数据已最终确认，请同步到 ES
    private String id;
    private Long userId;
    private String title;
    private String content;
    private List<String> tags;
    private Integer type;
    private String cover;
    private Integer coverWidth;
    private Integer coverHeight;

    private String userNickname;
    private String userAvatar;
}