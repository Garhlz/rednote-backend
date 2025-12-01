package com.szu.afternoon3.platform.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// 【关键】强制覆盖配置：
// 1. enable=false -> 激活 LocalFileServiceImpl
// 2. path -> 指定一个测试专用的文件夹，避免污染开发环境
// 3. base-url -> 指定返回的前缀
@TestPropertySource(properties = {
        "aliyun.oss.enable=false",
        "file.upload.path=target/test-uploads",
        "file.upload.base-url=http://localhost:8080/test-uploads/"
})
public class CommonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 注意：这里不再需要 @MockBean AliyunOssService
    // Spring 会根据上面的 enable=false 自动注入 LocalFileServiceImpl

    @Test
    @DisplayName("测试文件上传接口 (集成测试 - 本地Mock模式)")
    public void testUploadInterface() throws Exception {
        // 1. 准备一个虚拟文件
        MockMultipartFile file = new MockMultipartFile(
                "file",                      // 参数名
                "test_controller.jpg",       // 原始文件名
                MediaType.IMAGE_JPEG_VALUE,  // Content-Type
                "Real Content Written to Disk".getBytes() // 文件内容
        );

        // 2. 发起 multipart 请求
        // 这次会真正调用 LocalFileServiceImpl 的逻辑，在 target/test-uploads 下生成文件
        mockMvc.perform(multipart("/api/common/upload")
                        .file(file))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                // 3. 验证返回的 URL 是否符合本地模式的格式
                // 预期: http://localhost:8080/test-uploads/yyyy/MM/dd/uuid.jpg
                .andExpect(jsonPath("$.data.url", containsString("http://localhost:8080/test-uploads/")))
                .andExpect(jsonPath("$.data.url", containsString(".jpg")));
    }
}