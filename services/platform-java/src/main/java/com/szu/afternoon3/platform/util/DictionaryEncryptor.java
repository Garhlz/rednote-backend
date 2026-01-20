package com.szu.afternoon3.platform.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 敏感词加密工具 (一次性运行)
 * 功能：读取 raw_dict 下的所有 txt -> 加密 -> 存入 src/main/resources/sensitive 下的 dat
 */
public class DictionaryEncryptor {

    // 密钥 (必须与 SensitiveWordFilter 中的保持一致)
    private static final byte[] KEY = "REDNOTE_SECURE_2025".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) {
        // 1. 源文件位置 (把你下载的那些 politics.txt, porn.txt 放在项目根目录的 raw_dict 文件夹里)
        File sourceDir = new File("raw_dict");
        
        // 2. 目标位置 (项目资源目录)
        String targetPath = "src/main/resources/sensitive";
        FileUtil.mkdir(targetPath); // 自动创建目录

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("❌ 错误：请在项目根目录创建文件夹 [raw_dict] 并放入txt词库文件！");
            return;
        }

        File[] files = sourceDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            System.err.println("❌ 错误：raw_dict 目录下没有找到 .txt 文件！");
            return;
        }

        System.out.println("🚀 开始加密处理，共发现 " + files.length + " 个文件...");

        for (File txtFile : files) {
            processFile(txtFile, targetPath);
        }
        
        System.out.println("✅ 全部处理完成！请检查 src/main/resources/sensitive 目录。");
        System.out.println("⚠️ 记得删除 raw_dict 文件夹，且不要提交它！");
    }

    private static void processFile(File txtFile, String targetPath) {
        // 1. 读取中文内容 (Hutool 默认使用 UTF-8，完美支持中文)
        String content = FileUtil.readString(txtFile, StandardCharsets.UTF_8);
        if (StrUtil.isBlank(content)) return;

        // 2. 转为字节数组
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        // 3. XOR 加密
        for (int i = 0; i < data.length; i++) {
            data[i] ^= KEY[i % KEY.length];
        }

        // 4. 生成新文件名 (politics.txt -> politics.dat)
        String newName = txtFile.getName().replace(".txt", ".dat");
        File targetFile = new File(targetPath, newName);

        // 5. 写入文件
        FileUtil.writeBytes(data, targetFile);
        System.out.println("   [加密成功] " + txtFile.getName() + " -> " + newName);
    }
}