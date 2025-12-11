package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 1. 去掉 extends ApplicationEvent
// 2. 使用 Lombok 生成全参/无参构造
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDeleteEvent {
    // 3. 去掉 final 关键字（为了让无参构造 + Setter 能正常工作）
    private Long userId;
}