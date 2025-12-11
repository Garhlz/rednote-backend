# 第一周迭代测试报告 (First Iteration Test Report)

## 1. 测试概览 (Overview)

本报告针对 **Afternoon3 平台项目** 第一周迭代内容进行测试。
测试对象基于 `Afternoon3.Apifox (1).json` 定义的 API 接口，主要涵盖 **用户认证 (Auth)** 与 **用户中心 (User)** 模块。

### 1.1 测试范围
- **用户注册与登录**: 微信登录(Mock), 账号密码登录, 测试账号注册。
- **安全设置**: 邮箱绑定, 验证码发送, 密码设置与修改。
- **个人资料**: 个人信息查看, 资料修改 (昵称, 简介, 性别, 生日)。

### 1.2 测试环境
- **Operating System**: Windows
- **Database**: PostgreSQL (主库), MongoDB (业务数据), Redis (缓存/Session)
- **Backend**: Spring Boot 3.x
- **Testing Frameworks**: JUnit 5, Mockito, Python Requests

---

## 2. 白盒测试 (White-Box Testing)

白盒测试侧重于代码内部逻辑、数据流转以及异常处理的正确性。我们使用 Spring Boot Test 进行集成测试，并 Mock 了邮件服务。

### 2.1 测试类设计
我们创建了 `FirstIterationWhiteBoxTest.java`，模拟完整的用户生命周期。

**测试路径 (Test Path):**
1.  **创建用户**: 直接操作数据库插入初始用户 (模拟微信授权后状态)。
2.  **绑定邮箱**:
    -   Mock `JavaMailSender` 防止真实发送邮件。
    -   调用 `AuthService.sendEmailCode` 生成验证码。
    -   验证验证码是否正确存入 Redis。
    -   调用 `UserService.bindEmail` 完成绑定。
3.  **设置密码**:
    -   验证 Redis 中的 Token 校验逻辑。
    -   调用 `UserService.setPasswordWithCode` 设置加密密码。
    -   使用 `BCrypt` 验证数据库中存储的密码 Hash 是否正确。
4.  **资料管理**:
    -   调用 `UserService.updateProfile` 修改信息。
    -   调用 `UserService.getUserProfile` 获取视图对象 (VO) 并校验字段。

### 2.2 关键代码片段 (Key Code Snippets)

```java
// 验证 Redis 验证码逻辑
String codeKey = "verify:code:" + email;
String realCode = redisTemplate.opsForValue().get(codeKey);
Assertions.assertNotNull(realCode, "Redis 中未找到验证码");

// 验证密码加密
Assertions.assertTrue(BCrypt.checkpw("WhiteBoxPass123", updatedUser.getPassword()));
```

### 2.3 预期输出
测试运行后，控制台将输出清晰的步骤日志，显示每一步的执行结果与断言状态。

**测试执行结果示例 (Test Execution Log):**
```text
[Step 1] 创建初始用户 (模拟微信登录)
   -> 用户创建成功, ID: 20

[Step 2] 绑定邮箱流程
   -> 请求发送验证码 (邮件服务已Mock)
   -> 验证码已存入 Redis: 859229
   -> 邮箱绑定成功: wb_test_...@163.com

[Step 3] 设置初始密码
   -> 密码设置成功 (BCrypt加密验证通过)

[Step 4] 修改个人资料
   -> 昵称已更新: WB_Updated_Name

[Step 5] 获取个人资料视图
   -> 获取资料成功: UserProfileVO(userId=20, nickname=WB_Updated_Name, ...)

==================================================
            ✅ 白盒测试全部通过
==================================================
```

---

## 3. 黑盒测试 (Black-Box Testing)

黑盒测试将系统视为“黑箱”，通过 HTTP API 验证系统对外部请求的响应是否符合预期。

### 3.1 测试工具
编写了自动化测试脚本 `black_box_test.py`，使用 Python `requests` 库模拟客户端行为。

### 3.2 测试流程 (Test Scenario)
脚本按顺序执行以下操作，任何一步失败都会立即终止并报错：

1.  **注册测试账号 (`POST /api/auth/test/register`)**:
    -   创建一个全新的测试用户 (邮箱+密码)。
    -   **断言**: 返回 HTTP 200 及用户 ID。
2.  **账号登录 (`POST /api/auth/login/account`)**:
    -   使用刚注册的账号密码登录。
    -   **断言**: 返回有效的 JWT Token。
3.  **获取资料 (`GET /api/user/profile`)**:
    -   携带 Token 请求受保护接口。
    -   **断言**: 返回的昵称与注册时一致。
4.  **更新资料 (`PUT /api/user/profile`)**:
    -   修改昵称、简介、生日等。
    -   **断言**: 返回 HTTP 200 成功状态。
5.  **验证更新 (`GET /api/user/profile`)**:
    -   再次获取资料。
    -   **断言**: 返回的数据必须是更新后的新值。

### 3.3 运行方式
确保后端服务已在 `localhost:8080` 启动，然后在终端运行：

```bash
python black_box_test.py
```

**测试执行结果示例 (Test Execution Log):**
```text
Starting Black-Box Test against http://localhost:8080
Test User: blackbox_1765430506@test.com / TestPassword123

=== Step 1: Create Test User ===
✅ Success
User Created with ID: 21

=== Step 2: Login ===
✅ Success
Token acquired: eyJ0eXAiOiJKV1QiLCJh...

=== Step 3: Get User Profile ===
✅ Success
Profile: { "nickname": "BlackBoxUser_1765430506", ... }

=== Step 4: Update User Profile ===
✅ Success

=== Step 5: Verify Profile Update ===
✅ Success
Nickname updated to: Updated_BlackBoxUser_1765430506

========================================
       ALL BLACK-BOX TESTS PASSED
========================================
```

---

## 4. 结论 (Conclusion)

通过白盒与黑盒的双重验证，确认第一周迭代的核心功能（认证、用户管理）逻辑正确，接口响应符合 API 文档规范，具备交付条件。

- **白盒测试** 保证了业务逻辑组件（Service/Mapper）的内部正确性与数据一致性。
- **黑盒测试** 保证了对外接口（Controller/Security）的可用性与端到端流程的通畅。
