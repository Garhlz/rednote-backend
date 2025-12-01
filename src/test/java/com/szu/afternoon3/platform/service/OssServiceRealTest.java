package com.szu.afternoon3.platform.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;

@SpringBootTest
// @Disabled // ⚠️ 平时开发建议加上这个注解，避免每次 mvn package 都真的上传文件。想测的时候手动注释掉。
public class OssServiceRealTest {

    @Autowired
    private AliyunOssService aliyunOssService;

    @Test
    public void testRealUpload() {
        System.out.println("========== 开始真实 OSS 上传测试 ==========");

        try {
            // 1. 创建一个模拟的 MultipartFile
            // 这里我们直接传一段简单的文本字节作为文件内容
            String content = "Hello OSS! This is a test file from Spring Boot Test.";
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "real_test.txt",
                    "text/plain",
                    content.getBytes()
            );

            // 2. 调用真实 Service
            String url = aliyunOssService.uploadFile(file);

            // 3. 打印结果
            System.out.println("✅ 上传成功！");
            System.out.println("访问地址: " + url);
            System.out.println("请复制上面的 URL 到浏览器访问，看能否下载或预览。");

        } catch (Exception e) {
            System.err.println("❌ 上传失败！请检查 application.yml 里的阿里云配置。");
            e.printStackTrace();
        }

        System.out.println("========== 测试结束 ==========");
    }
}