package com.szu.afternoon3.platform.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserSearchVO extends UserInfo {
    /**
     * 我是否关注了他
     * true: 显示 "已关注"
     * false: 显示 "关注"
     */
    private Boolean isFollowed;

    /**
     * 他是否关注了我
     * true: 显示 "对方关注了你" 或 结合 isFollowed=true 显示 "互相关注"
     */
    private Boolean isFollowingMe;
}