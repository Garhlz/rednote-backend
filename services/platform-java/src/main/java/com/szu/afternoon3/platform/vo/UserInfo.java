package com.szu.afternoon3.platform.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserInfo {
    private String userId; // 转String防止前端精度丢失
    private String nickname;
    private String avatar;
    private String email;
}
