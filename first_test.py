import requests
import json
import time
import sys
import redis
import os
import subprocess

# ================= 配置区域 (Configuration) =================

# API 配置
BASE_URL = "http://localhost:8080"
TIMESTAMP = int(time.time())

# 测试用户配置
TEST_EMAIL = f"test_{TIMESTAMP}@test.com"
TEST_PASSWORD = "TestPassword123"
TEST_NICKNAME = f"TestUser_{TIMESTAMP}"

# 数据库配置 (Docker 容器名)
# 如果是本地直连，请修改 mode 为 'local' 并配置 Host/Port
# 这里为了确保准确性，默认使用 'docker' 模式
DB_ACCESS_MODE = 'docker' 
DOCKER_PG_CONTAINER = 'local-postgres'
DOCKER_MONGO_CONTAINER = 'local-mongo'
DOCKER_REDIS_CONTAINER = 'local-redis'

# Redis 配置 (用于 direct connect)
REDIS_HOST = 'localhost'
REDIS_PORT = 6379
REDIS_PASSWORD = None 

# 输出颜色
GREEN = "\033[92m"
RED = "\033[91m"
CYAN = "\033[96m"
YELLOW = "\033[93m"
RESET = "\033[0m"

# 全局变量，用于在黑白盒之间传递数据
SHARED_DATA = {
    "token": None,
    "user_id": None,
    "email": TEST_EMAIL,
    "post_id": None,
    "uploaded_url": None
}

# ================= 工具函数 (Helper Functions) =================

def print_header(title):
    print(f"\n{CYAN}{'='*60}")
    print(f"   {title}")
    print(f"{'='*60}{RESET}")

def print_sub_step(msg):
    print(f"{YELLOW} -> {msg}{RESET}")

def print_success(msg):
    print(f"{GREEN}   ✅ {msg}{RESET}")

def print_error(msg):
    print(f"{RED}   ❌ {msg}{RESET}")

def check_response_success(resp, context_msg=""):
    """
    检查 HTTP 响应是否成功
    """
    if resp.status_code != 200:
        print_error(f"{context_msg} HTTP Status Error: {resp.status_code}, Body: {resp.text}")
        return False, None
    
    try:
        data = resp.json()
    except:
        print_error(f"{context_msg} JSON Parse Error: {resp.text}")
        return False, None

    code = data.get('code')
    if code == 200:
        return True, data.get('data')
    else:
        print_error(f"{context_msg} Business Error: Code={code}, Msg={data.get('message')}")
        return False, data

def run_docker_command(container, command):
    """
    运行 docker exec 命令并返回输出
    """
    full_cmd = f"docker exec {container} {command}"
    try:
        # 使用 shell=True 需要注意安全，但在测试脚本中可接受
        result = subprocess.check_output(full_cmd, shell=True, stderr=subprocess.STDOUT)
        return result.decode('utf-8').strip()
    except subprocess.CalledProcessError as e:
        print_error(f"Docker command failed: {e.output.decode('utf-8')}")
        return None

# ================= 黑盒测试 (Black Box Testing) =================
# 纯粹通过 HTTP API 验证功能

def run_black_box_tests():
    print_header("📦 阶段一：黑盒测试 (Black Box Testing)")
    
    # 1. 注册与登录
    print_sub_step("测试用例 1: 用户注册与登录")
    
    # 1.1 注册
    register_payload = {
        "email": TEST_EMAIL,
        "password": TEST_PASSWORD,
        "nickname": TEST_NICKNAME
    }
    resp = requests.post(f"{BASE_URL}/api/auth/test/register", json=register_payload)
    success, _ = check_response_success(resp, "注册")
    if success:
        print_success(f"注册接口调用成功: {TEST_EMAIL}")
    else:
        return False

    # 1.2 登录
    login_payload = {
        "account": TEST_EMAIL,
        "password": TEST_PASSWORD
    }
    resp = requests.post(f"{BASE_URL}/api/auth/login/account", json=login_payload)
    success, data = check_response_success(resp, "登录")
    if success and data:
        SHARED_DATA["token"] = data.get('token')
        # LoginVO 中的 UserInfo 使用 userId 字段
        user_info = data.get('userInfo', {})
        SHARED_DATA["user_id"] = user_info.get('userId') or user_info.get('id')
        
        print_success(f"登录接口调用成功，获取 Token")
        # 如果登录接口没返回 ID，可能需要调 getUserInfo
        if not SHARED_DATA["user_id"]:
             # 尝试获取用户信息
             headers = {"Authorization": f"Bearer {SHARED_DATA['token']}"}
             resp_profile = requests.get(f"{BASE_URL}/api/user/profile", headers=headers)
             s, d = check_response_success(resp_profile, "获取个人信息")
             if s and d:
                 SHARED_DATA["user_id"] = d.get('id')
    else:
        return False

    # 2. 文件上传
    print_sub_step("测试用例 2: 文件上传")
    if not SHARED_DATA["token"]:
        print_error("无 Token，跳过后续步骤")
        return False
        
    headers = {"Authorization": f"Bearer {SHARED_DATA['token']}"}
    
    # 创建临时文件
    temp_file = "test_upload.jpg"
    with open(temp_file, 'wb') as f:
        f.write(b'fake image content')
    
    files = {'file': open(temp_file, 'rb')}
    resp = requests.post(f"{BASE_URL}/api/common/upload", files=files, headers=headers)
    files['file'].close()
    os.remove(temp_file) # 清理
    
    success, data = check_response_success(resp, "文件上传")
    if success and data:
        SHARED_DATA["uploaded_url"] = data.get('url')
        print_success(f"上传成功: {SHARED_DATA['uploaded_url']}")
    else:
        print_error("上传失败")

    # 3. 发布帖子
    print_sub_step("测试用例 3: 发布帖子")
    post_payload = {
        "type": 0,
        "title": f"BlackBox Test Post {TIMESTAMP}",
        "content": "This is a test post content for black box testing.",
        "images": [SHARED_DATA["uploaded_url"]] if SHARED_DATA["uploaded_url"] else [],
        "tags": ["Test", "BlackBox"]
    }
    resp = requests.post(f"{BASE_URL}/api/post", json=post_payload, headers=headers)
    success, data = check_response_success(resp, "发布帖子")
    if success and data:
        SHARED_DATA["post_id"] = data # 假设直接返回 ID 字符串或对象
        # 如果 data 是 dict 且有 id 字段
        if isinstance(data, dict) and 'id' in data:
            SHARED_DATA["post_id"] = data['id']
        print_success(f"发布帖子成功 ID: {SHARED_DATA['post_id']}")
    else:
        print_error("发布帖子失败")

    return True

# ================= 白盒测试 (White Box Testing) =================
# 连接数据库，验证数据是否正确落库

def run_white_box_tests():
    print_header("🔍 阶段二：白盒测试 (White Box Testing)")
    
    # 1. 验证 PostgreSQL (用户数据)
    print_sub_step("验证 PostgreSQL 数据 (Users 表)")
    
    # 使用 docker exec 查询，避免本地端口冲突问题
    # -t: 只打印行 (tuples only)
    # -A: 不对齐 (unaligned output)
    # -c: 执行 SQL
    cmd = f'psql -U postgres -d platform_db -t -A -c "SELECT id, email, password FROM users WHERE email = \'{TEST_EMAIL}\'"'
    output = run_docker_command(DOCKER_PG_CONTAINER, cmd)
    
    if output:
        try:
            # Output format: id|email|password
            parts = output.strip().split('|')
            if len(parts) >= 3:
                db_id, db_email, db_pass = parts[0], parts[1], parts[2]
                print_success(f"PostgreSQL 用户记录存在: ID={db_id}, Email={db_email}")
                
                if str(db_id) == str(SHARED_DATA["user_id"]):
                    print_success("User ID 与 API 返回一致")
                else:
                    print_error(f"User ID 不一致: API={SHARED_DATA['user_id']}, DB={db_id}")
                    
                if db_pass.startswith("$2a$"):
                    print_success("密码已加密存储 (BCrypt)")
                else:
                    print_error(f"密码存储异常: {db_pass}")
            else:
                print_error(f"PostgreSQL 输出格式异常: {output}")
        except Exception as e:
            print_error(f"解析 PostgreSQL 输出失败: {e}")
    else:
        print_error(f"PostgreSQL 中未找到用户: {TEST_EMAIL}")
        # Debug list users
        debug_cmd = 'psql -U postgres -d platform_db -t -A -c "SELECT email FROM users LIMIT 5"'
        debug_out = run_docker_command(DOCKER_PG_CONTAINER, debug_cmd)
        print_sub_step(f"当前库中存在的用户 (Top 5): {debug_out.replace(chr(10), ', ') if debug_out else 'None'}")


    # 2. 验证 MongoDB (帖子数据)
    print_sub_step("验证 MongoDB 数据 (Posts 集合)")
    if SHARED_DATA["post_id"]:
        # 使用 mongosh 查询
        # EJSON.stringify 确保 ObjectId 等类型被转为标准 JSON 字符串
        # Query: findOne({_id: ObjectId('...')}) or findOne({_id: '...'})
        # 我们先尝试 ObjectId
        query = f"db.posts.findOne({{_id: ObjectId('{SHARED_DATA['post_id']}')}})"
        cmd = f"mongosh rednote --quiet --eval \"EJSON.stringify({query})\""
        
        output = run_docker_command(DOCKER_MONGO_CONTAINER, cmd)
        
        # 如果 ObjectId 查不到，尝试 String ID (兼容部分旧数据)
        if not output or output == 'null':
             query_str = f"db.posts.findOne({{_id: '{SHARED_DATA['post_id']}'}})"
             cmd_str = f"mongosh rednote --quiet --eval \"EJSON.stringify({query_str})\""
             output = run_docker_command(DOCKER_MONGO_CONTAINER, cmd_str)

        if output and output != 'null':
            try:
                post_doc = json.loads(output)
                print_success(f"MongoDB 帖子文档存在: ID={post_doc.get('_id')}")
                
                if post_doc.get('title') == f"BlackBox Test Post {TIMESTAMP}":
                    print_success("帖子标题一致")
                else:
                    print_error(f"帖子标题不一致: DB={post_doc.get('title')}")
                
                # Mongo userId 可能是 NumberLong，EJSON 可能会转成 {"$numberLong": "..."} 或者直接数字
                db_uid = post_doc.get('userId')
                if isinstance(db_uid, dict) and '$numberLong' in db_uid:
                    db_uid = db_uid['$numberLong']
                
                if str(db_uid) == str(SHARED_DATA['user_id']):
                    print_success("帖子作者 ID 一致")
                else:
                    print_error(f"帖子作者 ID 不一致: DB={db_uid}")

                if post_doc.get('status') == 1:
                     print_success("帖子状态正确 (已发布)")
                else:
                     print_error(f"帖子状态异常: {post_doc.get('status')}")
            except Exception as e:
                print_error(f"解析 MongoDB 输出失败: {e}, Output: {output}")
        else:
            print_error(f"MongoDB 中未找到帖子: {SHARED_DATA['post_id']}")
    else:
        print_sub_step("跳过 MongoDB 验证")

    # 3. 验证 Redis (缓存/Session)
    print_sub_step("验证 Redis 数据 (Cache)")
    try:
        # Redis 端口通常没有冲突，或者我们可以继续用 python redis 库
        # 如果 python redis 库连的是 6379，而 docker 也是 6379，且没有本地 redis，应该没问题。
        # 如果有冲突，也可以用 docker exec local-redis redis-cli get ...
        # 这里尝试直接连接，如果失败再用 docker exec
        
        r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, password=REDIS_PASSWORD, decode_responses=True)
        r.ping()
        print_success("Redis 连接正常 (Python Client)")
        
        user_cache_key = f"user:info:{SHARED_DATA['user_id']}"
        if r.exists(user_cache_key):
             print_success(f"Redis 用户缓存存在: {user_cache_key}")
        else:
             print_sub_step(f"Redis 用户缓存不存在: {user_cache_key}")
             
    except Exception as e:
        print_error(f"Redis Python 连接失败: {e}")
        # Fallback to Docker Exec
        print_sub_step("尝试使用 Docker Exec 检查 Redis")
        ping_out = run_docker_command(DOCKER_REDIS_CONTAINER, "redis-cli ping")
        if ping_out and "PONG" in ping_out:
            print_success("Redis 连接正常 (Docker Exec)")
        else:
            print_error("Redis Docker Exec 连接失败")

def main():
    print(f"\n🚀 开始全量测试脚本 (黑盒 + 白盒)")
    print(f"📄 测试时间: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(TIMESTAMP))}")
    print(f"🎯 目标环境: {BASE_URL}")

    # 运行黑盒
    bb_result = run_black_box_tests()
    
    if bb_result:
        # 运行白盒 (仅当黑盒产生有效数据时)
        run_white_box_tests()
    else:
        print_error("黑盒测试失败，终止白盒测试")

    print_header("🏁 测试结束")

if __name__ == "__main__":
    main()
