统一响应体设计：
{
  "code": 10200,        // 业务码 (5位数字，前3位对应HTTP状态，后2位细分)
  "message": "操作成功", // 人类可读的提示
  "data": { ... }       // 数据
}

{
  "code": 10200,       
  "message": "操作成功", 
  "data": null     
}
不需要success字段了，前端可以直接通过code判断是否成功

以下是api规范的json版本
```json
{
  "200": [
    {
      "business_code": 200,
      "message": "操作成功",
      "description": "请求成功"
    }
  ],
  "400": [
    {
      "business_code": 40001,
      "message": "请求参数错误",
      "description": "必填项缺失、格式不对（如邮箱格式错）"
    },
    {
      "business_code": 40002,
      "message": "验证码错误或已失效",
      "description": "绑定邮箱/重置密码时，验证码输错"
    },
    {
      "business_code": 40003,
      "message": "用户名或密码错误",
      "description": "账号登录失败"
    },
    {
      "business_code": 40004,
      "message": "微信授权失败，请重试",
      "description": "code 无效或过期，需前端重新 wx.login"
    },
    {
      "business_code": 40005,
      "message": "密码强度不符合要求",
      "description": "密码强度不够（比如太短、没大写字母）是一个很常见的业务校验"
    }
  ],
  "401": [
    {
      "business_code": 40100,
      "message": "请先登录",
      "description": "Header 没带 Token"
    },
    {
      "business_code": 40101,
      "message": "登录已过期，请重新登录",
      "description": "Token 过期或非法"
    }
  ],
  "403": [
    {
      "business_code": 40301,
      "message": "账号已被禁用",
      "description": "users.status 为 banned"
    }
  ],
  "404": [
    {
      "business_code": 40401,
      "message": "该账号未注册",
      "description": "账号登录时，邮箱不存在"
    },
    {
      "business_code": 40402,
      "message": "资源不存在",
      "description": "访问了不存在的帖子或评论"
    }
  ],
  "409": [
    {
      "business_code": 40901,
      "message": "该邮箱已被其他账号绑定",
      "description": "绑定邮箱时，发现邮箱已存在"
    }
  ],
  "429": [
    {
      "business_code": 42901,
      "message": "操作太频繁，请稍后再试",
      "description": "发送验证码频率过高 (限流)"
    }
  ],
  "500": [
    {
      "business_code": 50000,
      "message": "服务器开小差了",
      "description": "后端代码抛出未捕获异常 (空指针、DB连不上)"
    },
    {
      "business_code": 50001,
      "message": "邮件服务异常",
      "description": "第三方服务发送邮件可能会失败（比如网断了或者达到上限）"
    }
  ]
}
```



