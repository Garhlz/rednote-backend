package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.vo.PostVO;
import com.szu.afternoon3.platform.dto.PostCreateDTO;

import java.util.List;
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

    Map<String, Object> searchPosts(String keyword, Integer page, Integer size);

    // 获取帖子详情
    PostVO getPostDetail(String postId);

    /**
     * 获取中文搜索候选词
     * @param keyword 关键词
     * @return 建议列表 (Tags + Titles)
     */
    List<String> getSearchSuggestions(String keyword);

    /**
     * 获取热门标签
     * @param limit 获取数量
     * @return 标签列表
     */
    List<String> getHotTags(int limit);

    /**
     * 获取用户的帖子列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页数量
     */
    Map<String, Object> getUserPostList(String userId, Integer page, Integer size);

    /**
     * 发布帖子
     * @param dto 创建参数
     * @return 生成的帖子ID
     */
    String createPost(PostCreateDTO dto);

    void deletePost(String postId);
}