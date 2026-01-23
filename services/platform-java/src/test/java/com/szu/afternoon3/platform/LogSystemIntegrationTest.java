package com.szu.afternoon3.platform;
import org.junit.jupiter.api.Disabled;

import cn.hutool.json.JSONUtil;
import com.szu.afternoon3.platform.dto.AccountLoginDTO;
import com.szu.afternoon3.platform.entity.mongo.ApiLogDoc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Disabled("migrated to gateway/user-rpc")
public class LogSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    public void cleanLogs() {
        // 为了保证测试准确，每次测试前清空 api_logs 集合
        // 注意：不要在生产环境跑这个测试！
        mongoTemplate.dropCollection(ApiLogDoc.class);
    }

    @Test
    @DisplayName("测试：操作日志正常记录 & SpEL表达式解析")
    public void testOperationLogSuccess() throws Exception {
        // 1. 准备请求参数 (模拟管理员登录接口，因为它有 bizId="#loginDTO.account")
        String testAccount = "admin_test_" + System.currentTimeMillis();
        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setAccount(testAccount);
        loginDTO.setPassword("123456");

        // 2. 发起请求
        // 即使登录失败(404/403)也没关系，AOP 依然会记录日志
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSONUtil.toJsonStr(loginDTO)))
                .andDo(print()); // 打印请求详情

        // 3. 【关键】等待异步处理
        // 因为 AOP -> RabbitMQ -> MongoDB 是异步的，需要给一点时间让消息落地
        System.out.println("⏳ 等待日志异步落库...");
        Thread.sleep(2000); 

        // 4. 从 MongoDB 查询日志
        Query query = new Query(Criteria.where("bizId").is(testAccount));
        List<ApiLogDoc> logs = mongoTemplate.find(query, ApiLogDoc.class);

        // 5. 断言验证
        Assertions.assertFalse(logs.isEmpty(), "MongoDB 中未找到对应 bizId 的日志，日志系统可能未生效！");
        
        ApiLogDoc log = logs.get(0);
        System.out.println("✅ 成功获取日志: " + JSONUtil.toJsonStr(log));

        Assertions.assertEquals("后台认证", log.getModule(), "模块名称解析错误");
        Assertions.assertEquals("管理员登录", log.getDescription(), "操作描述解析错误");
        Assertions.assertEquals("ADMIN_OPER", log.getLogType(), "日志类型错误");
        Assertions.assertEquals("/admin/auth/login", log.getUri(), "请求URI错误");
        Assertions.assertNotNull(log.getTimeCost(), "耗时未记录");
    }

    @Test
    @DisplayName("测试：通用请求(无注解)日志记录")
    public void testNormalLogWithoutAnnotation() throws Exception {
        // 访问一个没有 @OperationLog 注解的接口 (假设 /api/post/search 没有注解或我们只看通用逻辑)
        // 实际上你所有的 Controller 都被 AOP 拦截了
        
        mockMvc.perform(post("/api/auth/logout") // 登出接口通常比较简单
                        .header("Authorization", "Bearer fake_token"))
                .andDo(print());

        System.out.println("⏳ 等待日志异步落库...");
        Thread.sleep(2000);

        // 查询最新的日志
        Query query = new Query().with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        ApiLogDoc log = mongoTemplate.findOne(query, ApiLogDoc.class);

        Assertions.assertNotNull(log, "未生成任何日志");
        // 如果有注解，module 是注解里的值；如果没有，代码里写死的是 "通用"
        // 你的 AuthController.logout 上有注解，所以这里应该是 "认证模块"
        // 我们可以验证它是否记录了 URI
        Assertions.assertEquals("/api/auth/logout", log.getUri());
        System.out.println("✅ 通用日志测试通过: " + log.getDescription());
    }
}