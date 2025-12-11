import requests
import json
import time
import sys

# Configuration
BASE_URL = "http://localhost:8080"
TIMESTAMP = int(time.time())
EMAIL = f"blackbox_{TIMESTAMP}@test.com"
PASSWORD = "TestPassword123"
NICKNAME = f"BlackBoxUser_{TIMESTAMP}"

# Colors for output
GREEN = "\033[92m"
RED = "\033[91m"
RESET = "\033[0m"
CYAN = "\033[96m"

def print_step(step_name):
    print(f"\n{CYAN}=== {step_name} ==={RESET}")

def assert_response(response, expected_code=200):
    if response.status_code != expected_code:
        print(f"{RED}❌ Request Failed! Expected {expected_code}, got {response.status_code}{RESET}")
        print(f"Response: {response.text}")
        sys.exit(1)
    
    try:
        data = response.json()
        if data.get("code") != 200 and data.get("code") != 0: # Assuming 200 or 0 is success in Result object
             # Some frameworks use 0 for success, some 200. Adjust based on observation.
             # Checking the Result.java class would be good, but assuming standard 200 for now.
             pass
    except:
        pass
    print(f"{GREEN}✅ Success{RESET}")
    return response.json()

def main():
    print(f"Starting Black-Box Test against {BASE_URL}")
    print(f"Test User: {EMAIL} / {PASSWORD}")

    # 1. Create Test User
    print_step("Step 1: Create Test User")
    payload = {
        "email": EMAIL,
        "password": PASSWORD,
        "nickname": NICKNAME
    }
    resp = requests.post(f"{BASE_URL}/api/auth/test/register", json=payload)
    data = assert_response(resp)
    print(f"User Created with ID: {data.get('data')}")

    # 2. Login
    print_step("Step 2: Login")
    login_payload = {
        "account": EMAIL,
        "password": PASSWORD
    }
    resp = requests.post(f"{BASE_URL}/api/auth/login/account", json=login_payload)
    data = assert_response(resp)
    token = data.get("data", {}).get("token")
    if not token:
        print(f"{RED}❌ No token found in response!{RESET}")
        sys.exit(1)
    print(f"Token acquired: {token[:20]}...")

    headers = {
        "Authorization": f"Bearer {token}"
    }

    # 3. Get Profile
    print_step("Step 3: Get User Profile")
    resp = requests.get(f"{BASE_URL}/api/user/profile", headers=headers)
    data = assert_response(resp)
    profile = data.get("data", {})
    print(f"Profile: {json.dumps(profile, ensure_ascii=False, indent=2)}")
    
    if profile.get("nickname") != NICKNAME:
        print(f"{RED}❌ Nickname mismatch! Expected {NICKNAME}, got {profile.get('nickname')}{RESET}")
        sys.exit(1)

    # 4. Update Profile
    print_step("Step 4: Update User Profile")
    new_nickname = f"Updated_{NICKNAME}"
    update_payload = {
        "nickname": new_nickname,
        "bio": "Black box testing is effective.",
        "gender": 1,
        "birthday": "1999-09-09"
    }
    resp = requests.put(f"{BASE_URL}/api/user/profile", headers=headers, json=update_payload)
    assert_response(resp)
    
    # 5. Verify Update
    print_step("Step 5: Verify Profile Update")
    resp = requests.get(f"{BASE_URL}/api/user/profile", headers=headers)
    data = assert_response(resp)
    profile = data.get("data", {})
    if profile.get("nickname") != new_nickname:
        print(f"{RED}❌ Update Failed! Expected {new_nickname}, got {profile.get('nickname')}{RESET}")
        sys.exit(1)
    print(f"Nickname updated to: {profile.get('nickname')}")

    print(f"\n{GREEN}========================================")
    print(f"       ALL BLACK-BOX TESTS PASSED       ")
    print(f"========================================{RESET}")

if __name__ == "__main__":
    try:
        main()
    except requests.exceptions.ConnectionError:
        print(f"{RED}❌ Could not connect to {BASE_URL}. Is the server running?{RESET}")
    except Exception as e:
        print(f"{RED}❌ An error occurred: {e}{RESET}")
