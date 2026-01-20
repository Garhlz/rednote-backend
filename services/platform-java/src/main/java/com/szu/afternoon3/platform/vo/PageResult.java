package com.szu.afternoon3.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应包装类
 * @param <T> 列表项的类型 (如 UserInfo, PostVO)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    
    private List<T> records; // 数据列表
    private Long total;      // 总条数
    private Integer current; // 当前页
    private Integer size;    // 每页大小

    // 静态辅助方法：快速返回空页
    public static <T> PageResult<T> empty(Integer current, Integer size) {
        return new PageResult<>(Collections.emptyList(), 0L, current, size);
    }
    
    // 静态辅助方法：快速构建
    public static <T> PageResult<T> of(List<T> records, Long total, Integer current, Integer size) {
        return new PageResult<>(records, total, current, size);
    }
}