package com.szu.afternoon3.platform.vo;

import lombok.Data;
import java.util.List;

@Data
public class AdminStatsVO {
    // 1. 顶部卡片数据
    private Long todayNewUsers;
    private Long todayNewPosts;

    // 2. 折线图/柱状图数据 (X轴: 日期, Y轴: 数量)
    private ChartDataVO userTrend;
    private ChartDataVO postTrend;

    // 3. 饼图数据 (用户地区分布)
    private List<NameValueVO> regionStats;

    // --- 内部静态类 ---

    @Data
    public static class ChartDataVO {
        private List<String> dates; // ["12-01", "12-02", ...]
        private List<Long> values;  // [10, 5, ...]
    }

    @Data
    public static class NameValueVO {
        private String name;  // e.g. "广东"
        private Long value;   // e.g. 100
        
        public NameValueVO(String name, Long value) {
            this.name = name;
            this.value = value;
        }
    }
}