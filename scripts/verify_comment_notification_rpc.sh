#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8090}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
POST_ID="${POST_ID:-}"
COMMENT_CONTENT="${COMMENT_CONTENT:-RPC联调测试评论}"

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "ACCESS_TOKEN is required"
  exit 1
fi

if [[ -z "$POST_ID" ]]; then
  echo "POST_ID is required"
  exit 1
fi

_auth=(-H "Authorization: Bearer ${ACCESS_TOKEN}" -H 'Content-Type: application/json')

echo "[1/6] create comment"
create_resp=$(curl --noproxy '*' -sS -X POST "${GATEWAY_URL}/api/comment" "${_auth[@]}" -d "{\"postId\":\"${POST_ID}\",\"content\":\"${COMMENT_CONTENT}\"}")
echo "$create_resp"
comment_id=$(python - <<'PY' "$create_resp"
import json,sys
obj=json.loads(sys.argv[1])
print(obj.get('data',{}).get('id',''))
PY
)
if [[ -z "$comment_id" ]]; then
  echo "failed to create comment"
  exit 1
fi

echo "[2/6] list root comments"
curl --noproxy '*' -sS "${GATEWAY_URL}/api/comment/list?postId=${POST_ID}&page=1&size=10" -H "Authorization: Bearer ${ACCESS_TOKEN}" | python -m json.tool

echo "[3/6] delete comment"
curl --noproxy '*' -sS -X DELETE "${GATEWAY_URL}/api/comment/${comment_id}" -H "Authorization: Bearer ${ACCESS_TOKEN}" | python -m json.tool

echo "[4/6] unread count"
curl --noproxy '*' -sS "${GATEWAY_URL}/api/message/unread-count" -H "Authorization: Bearer ${ACCESS_TOKEN}" | python -m json.tool

echo "[5/6] notifications list"
notif_resp=$(curl --noproxy '*' -sS "${GATEWAY_URL}/api/message/notifications?page=1&size=10" -H "Authorization: Bearer ${ACCESS_TOKEN}")
echo "$notif_resp" | python -m json.tool

first_notif_id=$(python - <<'PY' "$notif_resp"
import json,sys
obj=json.loads(sys.argv[1])
records=obj.get('data',{}).get('records',[])
print(records[0]['id'] if records else '')
PY
)

echo "[6/6] mark notifications read"
if [[ -n "$first_notif_id" ]]; then
  curl --noproxy '*' -sS -X PUT "${GATEWAY_URL}/api/message/read" "${_auth[@]}" -d "{\"ids\":[\"${first_notif_id}\"]}" | python -m json.tool
else
  curl --noproxy '*' -sS -X POST "${GATEWAY_URL}/api/message/read" -H "Authorization: Bearer ${ACCESS_TOKEN}" | python -m json.tool
fi

echo "verification done"
