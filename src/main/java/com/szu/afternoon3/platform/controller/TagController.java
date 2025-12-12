package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签控制器
 * 提供热门标签等元数据查询
 */
@RestController
@RequestMapping("/api/tag")
public class TagController {

    @Autowired
    private PostService postService;

    /**
     * 获取热门标签
     */
    @GetMapping("/hot")
    @OperationLog(module = "标签模块", description = "获取热门标签")
    public Result<List<String>> getHotTags() {
        List<String> tags = postService.getHotTags(10);
        return Result.success(tags);
    }
}