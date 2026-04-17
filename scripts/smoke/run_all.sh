#!/usr/bin/env bash
# scripts/smoke/run_all.sh
# 依次执行所有冒烟测试脚本，汇总结果
# 用法：./scripts/smoke/run_all.sh [gateway_url]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GW="${1:-http://localhost:8888}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL=0
PASSED=0
FAILED_LIST=()

run_smoke() {
  local script="$1"
  local name
  name="$(basename "$script")"
  TOTAL=$((TOTAL+1))
  echo ""
  echo -e "${YELLOW}━━━ $name ━━━${NC}"
  if bash "$script" "$GW"; then
    PASSED=$((PASSED+1))
  else
    FAILED_LIST+=("$name")
  fi
}

# 按序执行所有 smoke 脚本
for script in "$SCRIPT_DIR"/[0-9]*.sh; do
  [ -f "$script" ] && run_smoke "$script"
done

# ---- 汇总 ----
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "总计: $TOTAL  通过: ${GREEN}$PASSED${NC}  失败: ${RED}$((TOTAL-PASSED))${NC}"
if [ "${#FAILED_LIST[@]}" -gt 0 ]; then
  echo -e "${RED}失败的测试:${NC}"
  for f in "${FAILED_LIST[@]}"; do
    echo "  - $f"
  done
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  exit 1
else
  echo -e "${GREEN}✅ 所有冒烟测试通过${NC}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  exit 0
fi
