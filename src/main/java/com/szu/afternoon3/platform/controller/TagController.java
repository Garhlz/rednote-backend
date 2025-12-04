package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tag")
public class TagController {

    @Autowired
    private PostService postService;

    /**
     * 获取热门标签
     * 对应接口: /api/tag/hot
     */
    @GetMapping("/hot")
    public Result<List<String>> getHotTags() {
        // 获取前 10 个热门标签
        List<String> tags = postService.getHotTags(10);
        return Result.success(tags);
    }
}