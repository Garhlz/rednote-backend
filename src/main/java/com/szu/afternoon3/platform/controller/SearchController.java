package com.szu.afternoon3.platform.controller;

import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private PostService postService;

    /**
     * 获取搜索历史
     * 对应接口: GET /api/search/history
     */
    @GetMapping("/history")
    public Result<List<String>> getSearchHistory() {
        Long userId = UserContext.getUserId();
        // 如果未登录，返回空列表而不是报错，提升体验（视业务需求而定）
        if (userId == null) {
            // throw new AppException(ResultCode.UNAUTHORIZED);
            // 或者：
            return Result.success(List.of());
        }

        List<String> history = postService.getSearchHistory(userId);
        return Result.success(history);
    }

    /**
     * 清空搜索历史
     * 对应接口: DELETE /api/search/history
     */
    @DeleteMapping("/history")
    public Result<Void> clearSearchHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        postService.clearSearchHistory(userId);
        return Result.success();
    }
}