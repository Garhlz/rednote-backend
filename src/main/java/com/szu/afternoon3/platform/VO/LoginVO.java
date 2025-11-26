package com.szu.afternoon3.platform.VO;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private UserInfo userInfo;
    private Boolean isNewUser;
    private Boolean hasPassword;

    @Data
    public static class UserInfo {
        private String userId; // 转String防止前端精度丢失
        private String nickname;
        private String avatar;
        private String email;
    }
}
