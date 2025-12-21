import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ================= 配置区域 =================
//const BASE_URL = 'http://8.148.145.178'; // 替换为你的服务器 IP 或 localhost
const BASE_URL = 'http://localhost:8080';
const TEST_USER = {
  account: "test5@test.com", // 确保数据库里有这个用户
  password: "123456"  // 确保密码正确
};
// ===========================================

export const options = {
  // 压测阶段配置
  stages: [
//    { duration: '10s', target: 10 }, // 热身：10秒内升到10个并发
//    { duration: '1m', target: 50 },  // 施压：保持50个并发跑1分钟 (你可以根据服务器情况调整)
//    { duration: '10s', target: 0 },  // 冷却：10秒内降回0
    { duration: '10s', target: 500 },
    { duration: '30s', target: 500 },
    { duration: '10s', target: 0 },
  ],
  // 阈值设置：如果95%的请求超过1秒，或者错误率超过1%，则算失败
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

// 1. 初始化阶段：只执行一次，负责登录获取 Token
export function setup() {
  const loginUrl = `${BASE_URL}/api/auth/login/account`;
  const payload = JSON.stringify(TEST_USER);
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(loginUrl, payload, params);

  // 检查登录是否成功
  const isLoginSuccess = check(res, {
    'setup login status is 200': (r) => r.status === 200,
    'setup has token': (r) => r.json('data.token') !== undefined,
  });

  if (!isLoginSuccess) {
    console.error(`Login failed! Status: ${res.status}, Body: ${res.body}`);
    // 如果登录都挂了，后面就没必要测了，直接抛错停止
    throw new Error('Setup failed: Unable to login');
  }

  // 返回 token 给所有虚拟用户 (VU) 共享
  return { token: res.json('data.token') };
}

// 2. 模拟用户行为阶段：会被反复执行
export default function (data) {
  // 从 setup 中获取 token
  const headers = {
    'Authorization': `Bearer ${data.token}`,
    'Content-Type': 'application/json'
  };

  // 行为 A: 刷首页帖子列表 (80% 的概率)
  group('Fetch Feed', function () {
    // 随机测试 'recommend' 或 'follow' tab
    const tab = 'recommend';
    const listRes = http.get(`${BASE_URL}/api/post/list?page=1&size=20&tab=${tab}`, { headers });

    const isListOk = check(listRes, {
      'list status 200': (r) => r.status === 200,
    });

    // 如果列表加载成功，提取帖子 ID，模拟“点进去看详情”
    if (isListOk) {
      const records = listRes.json('data.records');

      // 只有当列表里有帖子，且大概率 (50%) 才会点进去看
      if (records && records.length > 0 && Math.random() > 0.5) {
        // 随机选一个帖子
        const randomPost = records[Math.floor(Math.random() * records.length)];
        const postId = randomPost.id;

        // 模拟思考时间 (用户在看封面)
        sleep(Math.random() * 2);

        // 行为 B: 查看帖子详情 (高负载操作，会查 Mongo/Postgres)
        const detailRes = http.get(`${BASE_URL}/api/post/${postId}`, { headers });
        check(detailRes, {
          'detail status 200': (r) => r.status === 200,
        });
      }
    }
  });

  // 行为 C: 搜索 (20% 的概率，这是最考验 CPU/ES 的操作)
  if (Math.random() < 0.2) {
    group('Search Action', function () {
      const keywords = ['深圳','大学','技术','测试'];
      const kw = keywords[Math.floor(Math.random() * keywords.length)];
      const encodedKw = encodeURIComponent(kw);
      // 1. 模拟输入时的自动补全 (Suggest)
      http.get(`${BASE_URL}/api/post/search/suggest?keyword=${encodedKw}`, { headers });

      sleep(0.5); // 输完字停顿一下

      // 2. 真正的搜索
      const searchRes = http.get(`${BASE_URL}/api/post/search?keyword=${encodedKw}&page=1&size=20`, { headers });
            check(searchRes, {
                'search status 200': (r) => r.status === 200,
            });
    });
  }

  // 每次操作完休息一下，模拟真实用户，防止变成纯粹的 DDoS
  sleep(1);
}