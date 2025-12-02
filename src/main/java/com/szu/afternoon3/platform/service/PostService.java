package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.vo.PostVO;
import java.util.Map;

public interface PostService {
    /**
     * 获取帖子流列表
     * @param page 当前页 (1开始)
     * @param size 每页大小
     * @param tab 栏目类型 (recommend, follow)
     * @param tag 标签筛选
     * @return 包含分页信息的 Map
     */
    Map<String, Object> getPostList(Integer page, Integer size, String tab, String tag);
}