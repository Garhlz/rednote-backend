package com.szu.afternoon3.platform.util;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class WeChatUtil {

    // 填入你的小程序 ID 和 Secret
    // todo 修改为ie全局配置
    private final String appId = "你的appid";
    private final String secret = "你的secret";

    public String getOpenId(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session";
        // 使用 Hutool 的 HttpUtil 发送请求
        String response = HttpUtil.get(url, Map.of(
                "appid", appId,
                "secret", secret,
                "js_code", code,
                "grant_type", "authorization_code"
        ));

        JSONObject json = JSONUtil.parseObj(response);
        // todo 悠悠说微信的接口可能会返回text的http header而不是json,这里需要注意
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            log.error("微信登录出错: {}", response);
            throw new RuntimeException("微信授权失败");
        }
        return json.getStr("openid");
    }
}