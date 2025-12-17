import argparse
import pymongo
import requests
import time
import io
import os
import paramiko  # 必须引入

# ==========================================
# 🚑【核心修复补丁】🚑
# 强行给 paramiko 塞一个假的 DSSKey 类，防止 sshtunnel 崩溃
# 这段代码必须放在引入 sshtunnel 之前
# ==========================================
if not hasattr(paramiko, "DSSKey"):

    class DSSKey(paramiko.PKey):
        def asbytes(self):
            pass

        @property
        def can_sign(self):
            return False

        def get_name(self):
            return "ssh-dss"

        def get_bits(self):
            return 1024

        def sign_ssh_data(self, data):
            pass

        def verify_ssh_sig(self, data, msg):
            return False

        def write_private_key_file(self, filename, password=None):
            pass

        def write_private_key(self, file_obj, password=None):
            pass

    paramiko.DSSKey = DSSKey
# ==========================================

from PIL import Image
from sshtunnel import SSHTunnelForwarder

# ================= 远程配置区 (SSH) =================
SSH_HOST = "8.148.145.178"
SSH_USER = "elaine"
SSH_PORT = 22
SSH_PKEY_PATH = "~/.ssh/id_ed25519"
# 远程 Mongo 在服务器本地的地址
REMOTE_MONGO_BIND_HOST = "127.0.0.1"
REMOTE_MONGO_BIND_PORT = 27017

# ================= 本地配置区 (--local) =================
LOCAL_MONGO_HOST = "127.0.0.1"
LOCAL_MONGO_PORT = 27017

# ================= 通用数据库配置 =================
DB_NAME = "rednote"
COLLECTION_NAME = "posts"
# ====================================================


def core_process(db):
    """
    核心业务逻辑：传入数据库对象，执行修复
    无论是本地还是远程，处理逻辑是一样的
    """
    collection = db[COLLECTION_NAME]

    # 查找 coverWidth 不存在或为 0 的记录
    query = {
        "$or": [
            {"coverWidth": {"$exists": False}},
            {"coverWidth": 0},
            {"coverWidth": None},
        ]
    }

    count = collection.count_documents(query)
    print(f"🚀 发现 {count} 条数据需要修复...")

    if count == 0:
        return

    cursor = collection.find(query)

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }

    for doc in cursor:
        post_id = doc["_id"]
        cover_url = doc.get("cover", "")

        if not cover_url or "http" not in cover_url:
            continue

        # 跳过纯视频
        if cover_url.endswith(".mp4") and "x-oss-process" not in cover_url:
            print(f"⏭️ [跳过] ID: {post_id} 纯视频链接")
            continue

        try:
            response = requests.get(cover_url, headers=headers, timeout=10)

            if response.status_code == 200:
                image_data = io.BytesIO(response.content)

                with Image.open(image_data) as img:
                    width, height = img.size

                    collection.update_one(
                        {"_id": post_id},
                        {"$set": {"coverWidth": width, "coverHeight": height}},
                    )
                    print(f"✅ [成功] ID: {post_id} -> {width}x{height}")
            else:
                print(f"⚠️ [下载失败] ID: {post_id} HTTP {response.status_code}")

        except Exception as e:
            print(f"❌ [异常] ID: {post_id}: {str(e)}")

        time.sleep(0.2)


def run_via_ssh():
    """SSH 隧道模式运行"""
    # 1. 处理私钥路径
    private_key_path = os.path.expanduser(SSH_PKEY_PATH)
    print(f"🔑 [SSH模式] 正在加载私钥: {private_key_path}")

    # 2. 手动加载私钥 (双重保险)
    my_pkey = None
    try:
        my_pkey = paramiko.Ed25519Key.from_private_key_file(private_key_path)
    except Exception as e:
        print(f"❌ Ed25519 加载失败，尝试 RSA... ({e})")
        try:
            my_pkey = paramiko.RSAKey.from_private_key_file(private_key_path)
        except Exception as e2:
            print(f"💥 私钥加载失败: {e2}")
            return

    print(f"🔄 正在建立 SSH 隧道 ({SSH_HOST})...")

    with SSHTunnelForwarder(
        (SSH_HOST, SSH_PORT),
        ssh_username=SSH_USER,
        ssh_pkey=my_pkey,
        remote_bind_address=(REMOTE_MONGO_BIND_HOST, REMOTE_MONGO_BIND_PORT),
    ) as server:

        print(f"✅ SSH 隧道建立成功！本地映射端口: {server.local_bind_port}")

        # 连接到隧道端口
        local_uri = f"mongodb://127.0.0.1:{server.local_bind_port}/{DB_NAME}"
        client = pymongo.MongoClient(local_uri)
        db = client[DB_NAME]

        # 执行核心逻辑
        core_process(db)


def run_local():
    """本地直连模式运行"""
    print(f"🏠 [本地模式] 正在直连 MongoDB: {LOCAL_MONGO_HOST}:{LOCAL_MONGO_PORT}")

    local_uri = f"mongodb://{LOCAL_MONGO_HOST}:{LOCAL_MONGO_PORT}/{DB_NAME}"
    try:
        client = pymongo.MongoClient(local_uri, serverSelectionTimeoutMS=5000)
        # 简单测试连接
        client.server_info()
        print("✅ MongoDB 连接成功！")

        db = client[DB_NAME]
        core_process(db)

    except Exception as e:
        print(f"💥 本地 MongoDB 连接失败: {e}")
        print("💡 请检查本地 MongoDB 是否已启动 (docker ps 或 systemctl status mongodb)")


if __name__ == "__main__":
    # 解析命令行参数
    parser = argparse.ArgumentParser(description="修复 MongoDB 中的图片尺寸数据")
    parser.add_argument("--local", action="store_true", help="使用本地 MongoDB 模式运行 (不通过 SSH)")

    args = parser.parse_args()

    try:
        if args.local:
            run_local()
        else:
            run_via_ssh()

        print("🎉 修复完成。")
    except KeyboardInterrupt:
        print("\n🛑 用户手动停止")
    except Exception as e:
        print(f"💥 脚本崩溃: {e}")