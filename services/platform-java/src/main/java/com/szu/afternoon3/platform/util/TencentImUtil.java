package com.szu.afternoon3.platform.util;

import com.tencentyun.TLSSigAPIv2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TencentImUtil {

    @Value("${tencent.im.sdk-appid}")
    private long sdkAppId;

    @Value("${tencent.im.secret-key}")
    private String secretKey;

    @Value("${tencent.im.expire-time:604800}")
    private long expireTime;

    /**
     * 生成 UserSig
     * @param userId 用户ID (对应 IM 中的 UserID)
     * @return 签名字符串
     */
    public String genUserSig(String userId) {
        try {
            TLSSigAPIv2 api = new TLSSigAPIv2(sdkAppId, secretKey);
            return api.genUserSig(userId, expireTime);
        } catch (Exception e) {
            log.error("生成 UserSig 失败: userId={}", userId, e);
            // 既然是辅助功能，生成失败可以返回 null 或空串，也可以抛异常阻断登录
            return "";
        }
    }
}