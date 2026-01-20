package com.szu.afternoon3.platform.controller;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.annotation.OperationLog;
import com.szu.afternoon3.platform.common.Result;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索历史控制器
 * 处理用户搜索记录的增删查
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private PostService postService;

    /**
     * 获取搜索历史
     */
    @GetMapping("/history")
    @OperationLog(module = "搜索模块", description = "获取搜索历史")
    public Result<List<String>> getSearchHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.success(List.of());
        }
        List<String> history = postService.getSearchHistory(userId);
        return Result.success(history);
    }

    /**
     * 清空搜索历史
     */
    @DeleteMapping("/history")
    @OperationLog(module = "搜索模块", description = "清空搜索历史")
    public Result<Void> clearSearchHistory() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }
        postService.clearSearchHistory(userId);
        return Result.success();
    }

    /**
     * 删除单条搜索记录
     */
    @DeleteMapping("/history/item")
    @OperationLog(module = "搜索模块", description = "删除搜索记录", bizId = "#keyword")
    public Result<Void> deleteSearchHistoryItem(@RequestParam String keyword) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }
        if (StrUtil.isBlank(keyword)) {
            return Result.error(ResultCode.PARAM_ERROR.getCode(), "关键词不能为空");
        }
        postService.deleteSearchHistoryItem(userId, keyword);
        return Result.success();
    }
}