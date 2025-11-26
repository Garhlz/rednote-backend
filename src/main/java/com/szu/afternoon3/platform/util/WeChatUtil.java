package com.szu.afternoon3.platform.util;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class WeChatUtil {

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String secret;

    public String getOpenId(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("appid", appId);
        paramMap.put("secret", secret);
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");

        // 发送请求
        String response = HttpUtil.get(url, paramMap);
        log.info("微信接口返回: {}", response); // 打印日志方便调试

        // 解析 JSON
        JSONObject json = JSONUtil.parseObj(response);

        // 检查错误码
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            log.error("微信登录出错: code={}, msg={}", json.getInt("errcode"), json.getStr("errmsg"));
            throw new RuntimeException("微信授权失败: " + json.getStr("errmsg"));
        }

        return json.getStr("openid");
    }
}