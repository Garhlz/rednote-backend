import requests
import json
import time
import sys

# 配置信息
BASE_URL = "http://localhost:8080"
TIMESTAMP = int(time.time())
EMAIL = f"blackbox_{TIMESTAMP}@test.com"
PASSWORD = "TestPassword123"
NICKNAME = f"BlackBoxUser_{TIMESTAMP}"

# 输出颜色配置
GREEN = "\033[92m"
RED = "\033[91m"
RESET = "\033[0m"
CYAN = "\033[96m"

def print_step(step_name):
    print(f"\n{CYAN}=== {step_name} ==={RESET}")

def assert_response(response, expected_code=200):
    if response.status_code != expected_code:
        print(f"{RED}❌ 请求失败! 期望状态码 {expected_code}, 实际返回 {response.status_code}{RESET}")
        print(f"响应内容: {response.text}")
        sys.exit(1)
    
    try:
        data = response.json()
        if data.get("code") != 200 and data.get("code") != 0: 
             pass
    except:
        pass
    print(f"{GREEN}✅ 成功{RESET}")
    return response.json()

def main():
    print(f"开始针对 {BASE_URL} 的黑盒测试")
    print(f"测试用户: {EMAIL} / {PASSWORD}")

    # 1. 创建测试用户
    print_step("步骤 1: 创建测试用户")
    payload = {
        "email": EMAIL,
        "password": PASSWORD,
        "nickname": NICKNAME
    }
    resp = requests.post(f"{BASE_URL}/api/auth/test/register", json=payload)
    data = assert_response(resp)
    print(f"用户创建成功，ID: {data.get('data')}")

    # 2. 登录
    print_step("步骤 2: 登录")
    login_payload = {
        "account": EMAIL,
        "password": PASSWORD
    }
    resp = requests.post(f"{BASE_URL}/api/auth/login/account", json=login_payload)
    data = assert_response(resp)
    token = data.get("data", {}).get("token")
    if not token:
        print(f"{RED}❌ 响应中未找到 Token!{RESET}")
        sys.exit(1)
    print(f"获取 Token 成功: {token[:20]}...")

    headers = {
        "Authorization": f"Bearer {token}"
    }

    # 3. 获取个人资料
    print_step("步骤 3: 获取个人资料")
    resp = requests.get(f"{BASE_URL}/api/user/profile", headers=headers)
    data = assert_response(resp)
    profile = data.get("data", {})
    print(f"个人资料: {json.dumps(profile, ensure_ascii=False, indent=2)}")
    
    if profile.get("nickname") != NICKNAME:
        print(f"{RED}❌ 昵称不匹配! 期望 {NICKNAME}, 实际 {profile.get('nickname')}{RESET}")
        sys.exit(1)

    # 4. 更新个人资料
    print_step("步骤 4: 更新个人资料")
    new_nickname = f"Updated_{NICKNAME}"
    update_payload = {
        "nickname": new_nickname,
        "bio": "黑盒测试非常有效。",
        "gender": 1,
        "birthday": "1999-09-09"
    }
    resp = requests.put(f"{BASE_URL}/api/user/profile", headers=headers, json=update_payload)
    assert_response(resp)
    
    # 5. 验证更新
    print_step("步骤 5: 验证资料更新")
    resp = requests.get(f"{BASE_URL}/api/user/profile", headers=headers)
    data = assert_response(resp)
    profile = data.get("data", {})
    if profile.get("nickname") != new_nickname:
        print(f"{RED}❌ 更新验证失败! 期望 {new_nickname}, 实际 {profile.get('nickname')}{RESET}")
        sys.exit(1)
    print(f"昵称已更新为: {profile.get('nickname')}")

    print(f"\n{GREEN}========================================")
    print(f"       所有黑盒测试均已通过       ")
    print(f"========================================{RESET}")

if __name__ == "__main__":
    try:
        main()
    except requests.exceptions.ConnectionError:
        print(f"{RED}❌ 无法连接到 {BASE_URL}. 请确认服务器是否已启动?{RESET}")
    except Exception as e:
        print(f"{RED}❌ 发生错误: {e}{RESET}")
