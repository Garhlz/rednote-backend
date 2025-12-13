package com.szu.afternoon3.platform.vo;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditResultVO implements Serializable {
    /**
     * 审核结论: PASS(通过), BLOCK(违规), REVIEW(需人工复审)
     */
    private String conclusion;

    /**
     * 风险类型: 色情, 暴力, 政治, 广告, 谩骂, 无(Safe)
     */
    private String riskType;

    /**
     * 置信度 (0.0 - 1.0), 越高越确信
     */
    private Double confidence;

    /**
     * 详细建议/原因
     */
    private String suggestion;

    private String postId;

}