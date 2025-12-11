package com.szu.afternoon3.platform.controller;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.szu.afternoon3.platform.dto.AccountLoginDTO;
import com.szu.afternoon3.platform.dto.UserProfileUpdateDTO;
import com.szu.afternoon3.platform.dto.WechatLoginDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.util.WeChatUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc // 开启虚拟MVC调用
@Transactional // 测试结束后自动回滚数据库，保持环境干净
public class ApiIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserMapper userMapper;

        // 【核心】Mock掉微信工具类，不让它真正发请求
        @MockBean
        private WeChatUtil weChatUtil;

        // 预设一个测试用的 OpenID
        private static final String MOCK_OPENID = "mock_openid_123456";

        @BeforeEach
        public void setup() {
                // 配置 Mock 行为：当调用 getOpenId 时，不管传什么 code，都返回固定的 OpenID
                Mockito.when(weChatUtil.getOpenId(Mockito.anyString())).thenReturn(MOCK_OPENID);
        }

    @Test
    @DisplayName("测试微信一键登录(自动注册)")
    public void testWechatLogin_AutoRegister() throws Exception {
        // 1. 构造请求参数
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setCode("fake_code_123"); // 假的 code，反正被 Mock 拦截了

        // 2. 发起 POST 请求
        mockMvc.perform(post("/api/auth/login/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(loginDTO)))
                .andDo(print()) // 打印请求详情
                .andExpect(status().isOk()) // 期望 HTTP 200
                .andExpect(jsonPath("$.code").value(20000)) // 期望业务码 20000
                .andExpect(jsonPath("$.data.token").exists()) // 期望返回 token
                .andExpect(jsonPath("$.data.isNewUser").value(true)); // 期望是新用户
    }

    @Test
    @DisplayName("测试账号密码登录流程")
    public void testAccountLogin() throws Exception {
        // 1. 预先在数据库插入一个用户 (模拟已注册用户)
        String email = "test_login@szu.edu.cn";
        String rawPassword = "Password123";

        User user = new User();
        user.setNickname("LoginTester");
        user.setEmail(email);
        // 必须加密存储
        user.setPassword(BCrypt.hashpw(rawPassword));
        user.setRole("USER");
        user.setStatus(1);
        user.setIsDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // 2. 构造登录请求
        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setAccount(email);
        loginDTO.setPassword(rawPassword);

        // 3. 发送请求验证
        mockMvc.perform(post("/api/auth/login/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(loginDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.userInfo.email").value(email));
    }

    @Test
    @DisplayName("测试获取和修改个人资料(鉴权接口)")
    public void testUserProfileFlow() throws Exception {
        // === Step 1: 先通过微信登录获取 Token ===
        WechatLoginDTO loginDTO = new WechatLoginDTO();
        loginDTO.setCode("any_code");

        MvcResult result = mockMvc.perform(post("/api/auth/login/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(loginDTO)))
                .andExpect(status().isOk())
                .andReturn();

        // 提取 Token
        String responseStr = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JSONObject jsonObject = JSONUtil.parseObj(responseStr);
        String token = jsonObject.getByPath("data.token", String.class);

        System.out.println("获取到的 Token: " + token);

        // === Step 2: 携带 Token 获取个人资料 ===
        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + token)) // 设置 Header
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("微信用户")); // 默认昵称

        // === Step 3: 修改个人资料 ===
        UserProfileUpdateDTO updateDTO = new UserProfileUpdateDTO();
        updateDTO.setNickname("小青");
        updateDTO.setBio("Arch Linux User");
        updateDTO.setGender(2);

        mockMvc.perform(put("/api/user/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000));

        // === Step 4: 再次获取确认修改生效 ===
        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小青"))
                .andExpect(jsonPath("$.data.bio").value("Arch Linux User"));
    }
}