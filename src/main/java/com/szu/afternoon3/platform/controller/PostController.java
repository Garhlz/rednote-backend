package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/post")
public class PostController {

    @Autowired
    private PostService postService;

    /**
     * 获取首页帖子流
     * @param page 页码 (默认1)
     * @param size 每页数量 (默认20)
     * @param tab  "recommend" 或 "follow"
     * @param tag  标签筛选 (如果存在则忽略 tab)
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getPostList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "recommend") String tab,
            @RequestParam(required = false) String tag
    ) {
        Map<String, Object> data = postService.getPostList(page, size, tab, tag);
        return Result.success(data);
    }
}