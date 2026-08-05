#!/bin/bash

# ─────────────────────────────────────────────────────────────
#  FILE PROCESSOR SERVICE — E2E Validation Script
#  Valida estabilidad completa del MS incluyendo endpoints de
#  control (sync status, process status, daily SLP status).
# ─────────────────────────────────────────────────────────────

# ── Configuración ────────────────────────────────────────────
MS_URL="http://localhost:8085"
BASE_PATH="/api/v1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
MOCKS_PID_FILE="$SCRIPT_DIR/mocks.pid"
MS_PID_FILE="$SCRIPT_DIR/ms.pid"
VALIDATION_ERRORS=0

# ── Colores ──────────────────────────────────────────────────
C_RESET='\033[0m'
C_GREEN='\033[0;32m'
C_RED='\033[0;31m'
C_YELLOW='\033[0;33m'
C_CYAN='\033[0;36m'
C_BOLD='\033[1m'

# ── Helpers ──────────────────────────────────────────────────
pass() { printf "  ${C_GREEN}✔${C_RESET} %s\n" "$1"; }
fail() { printf "  ${C_RED}✘${C_RESET} %s\n" "$1"; VALIDATION_ERRORS=$((VALIDATION_ERRORS + 1)); }
info() { printf "  ${C_CYAN}→${C_RESET} %s\n" "$1"; }

# Llama a un endpoint de control, imprime el resultado y valida
# Uso: check_control_endpoint <label> <url> <expected_value_regex>
check_control_endpoint() {
    local label="$1"
    local url="$2"
    local expected="$3"
    local response
    response=$(curl -s --max-time 10 "$url")
    local exit_code=$?

    if [ $exit_code -ne 0 ] || [ -z "$response" ]; then
        fail "$label → no response (curl exit: $exit_code)"
        return
    fi

    if echo "$response" | grep -qE "$expected"; then
        pass "$label → $response"
    else
        fail "$label → unexpected value: '$response' (expected pattern: '$expected')"
    fi
}

# ── Cleanup ──────────────────────────────────────────────────
cleanup() {
    echo "Cleaning up..."
    [ -f "$MOCKS_PID_FILE" ] && kill -9 $(cat "$MOCKS_PID_FILE") 2>/dev/null
    [ -f "$MS_PID_FILE" ] && kill -9 $(cat "$MS_PID_FILE") 2>/dev/null
    /usr/sbin/lsof -ti :8085,3003,9003 | xargs kill -9 2>/dev/null || true
    rm -f "$MOCKS_PID_FILE" "$MS_PID_FILE"
    sleep 2
}

# Kill previous processes if they exist
cleanup

echo ""
printf "${C_BOLD}====================================================\n"
printf "  FILE PROCESSOR — E2E VALIDATION (MULTI-SCENARIO)\n"
printf "====================================================\n${C_RESET}"

# ── 1. Mocks ─────────────────────────────────────────────────
echo ""
echo "1. Starting Mocks..."
cd "$SCRIPT_DIR"
python3 mocks.py > mocks.log 2>&1 &
echo $! > "$MOCKS_PID_FILE"
sleep 3

# ── 2. Microservice ──────────────────────────────────────────
echo "2. Starting Microservice (Profile: dev)..."
cd "$ROOT_DIR"
./gradlew bootRun --args='--spring.profiles.active=dev --server.port=8085 --app.document-rest.endpoint=http://localhost:3003 --app.animal-rest.endpoint=http://localhost:3003 --app.soap.v2.endpoint=http://localhost:9003/soap/adminDocs' > "$SCRIPT_DIR/ms.log" 2>&1 &
echo $! > "$MS_PID_FILE"

until curl -s $MS_URL/actuator/health | grep -q "UP"; do
    printf "."
    sleep 5
done
echo -e "\nMS is UP!"

echo ""
printf "${C_BOLD}[CONTROL] Actuator Health Check${C_RESET}\n"
check_control_endpoint "actuator/health" \
    "$MS_URL/actuator/health" \
    '"status":"UP"'

# ── 3. Sync ──────────────────────────────────────────────────
echo ""
echo "3. Synchronizing Documents (GET /sync)..."
curl -s -H "use-case: SOAP" "$MS_URL${BASE_PATH}/products/sync/soap" > /dev/null
info "Waiting for sync to complete..."
until grep -q "Document sync completed" "$SCRIPT_DIR/ms.log"; do
    sleep 2
done
echo "   Sync completed!"

echo ""
printf "${C_BOLD}[CONTROL] Sync Status — type_job=soap${C_RESET}\n"
check_control_endpoint "sync/status/soap" \
    "$MS_URL${BASE_PATH}/products/sync/status/soap" \
    "exitoso|0"

# ── 4. First Run ─────────────────────────────────────────────
echo ""
echo "4. Processing Documents — FIRST RUN (GET /products/soap)..."
curl -s "$MS_URL${BASE_PATH}/products/soap" > /dev/null
info "Waiting for first run to complete..."
until grep -q "Pending documents processing completed" "$SCRIPT_DIR/ms.log"; do
    sleep 2
done
echo "   First run completed! Waiting 5s before next run..."
sleep 5

echo ""
printf "${C_BOLD}[CONTROL] Process Status — type_job=soap (after 1st run)${C_RESET}\n"
check_control_endpoint "process/status/soap (1st run)" \
    "$MS_URL${BASE_PATH}/products/process/status/soap" \
    "exitoso|0|error"

# ── 5. Second Run ────────────────────────────────────────────
echo "--- SECOND RUN LOGS START HERE ---" >> "$SCRIPT_DIR/ms.log"

echo ""
echo "5. Processing Documents — SECOND RUN (GET /products/soap)..."
curl -s "$MS_URL${BASE_PATH}/products/soap" > /dev/null
info "Waiting for second run to complete..."
until [ $(grep -c "Pending documents processing completed" "$SCRIPT_DIR/ms.log") -ge 2 ]; do
    sleep 2
done
echo "   Second run completed! Waiting 5s for processing to settle..."
sleep 5

echo ""
printf "${C_BOLD}[CONTROL] Process Status — type_job=soap (after 2nd run)${C_RESET}\n"
check_control_endpoint "process/status/soap (2nd run)" \
    "$MS_URL${BASE_PATH}/products/process/status/soap" \
    "exitoso|error"

# ── 6. Daily SLP Control Status ──────────────────────────────
echo ""
printf "${C_BOLD}[CONTROL] Daily SLP Process Status (sin ejecución previa)${C_RESET}\n"
info "Se espera STATUS_COMPLETED cuando no hay registros del día"
check_control_endpoint "process/status/daily (baseline)" \
    "$MS_URL${BASE_PATH}/products/process/status/daily" \
    "exitoso"

# ── 7. Sync Status Final ─────────────────────────────────────
echo ""
printf "${C_BOLD}[CONTROL] Sync Status Final — type_job=soap${C_RESET}\n"
check_control_endpoint "sync/status/soap (final)" \
    "$MS_URL${BASE_PATH}/products/sync/status/soap" \
    "exitoso|0|error"

# ── 8. Animal Processing ──────────────────────────────────────
echo ""
echo "8. Processing Animal Documents (GET /products/daily/animal)..."
curl -s "$MS_URL${BASE_PATH}/products/daily/animal" > /dev/null
info "Waiting for animal processing to complete..."
until grep -q "Animal Daily processing completed" "$SCRIPT_DIR/ms.log"; do
    sleep 2
done
echo "   Animal processing completed!"

echo ""
printf "${C_BOLD}[CONTROL] Animal Process Status — daily${C_RESET}\n"
check_control_endpoint "process/status/daily/animal" \
    "$MS_URL${BASE_PATH}/products/process/status/daily/animal" \
    "exitoso"

# ── 9. Test Results Summary ──────────────────────────────────
echo ""
printf "${C_BOLD}====================================================\n"
printf "                TEST RESULTS SUMMARY                \n"
printf "====================================================\n${C_RESET}"
printf "%-20s | %-15s | %-10s | %s\n" "DOCUMENT ID" "PRODUCT" "RESULT" "MESSAGE"
echo "------------------------------------------------------------------------------------"

grep "Document .* (Product: .*)" "$SCRIPT_DIR/ms.log" | grep -v "SUMMARY" | while read -r line; do
    STATUS=$(echo "$line" | sed -E 's/.*\[(SUCCESS|FAILURE|RETRYABLE_ERROR)\].*/\1/')
    DOC_ID=$(echo "$line" | sed -E 's/.*Document ([^ ]+).*/\1/')
    PROD_ID=$(echo "$line" | sed -E 's/.*Product: ([^)]+).*/\1/')
    STATE=$(echo "$line" | sed -E 's/.*-> ([^.]+).*/\1/')
    MSG=$(echo "$line" | sed -E 's/.*Messages: (.*)/\1/')

    FINAL_COLOR=$C_RESET
    [ "$STATUS" == "SUCCESS" ]         && FINAL_COLOR=$C_GREEN
    [ "$STATUS" == "FAILURE" ]         && FINAL_COLOR=$C_RED
    [ "$STATUS" == "RETRYABLE_ERROR" ] && FINAL_COLOR=$C_YELLOW

    printf "%-20s | %-15s | ${FINAL_COLOR}%-10s${C_RESET} | %s\n" "$DOC_ID" "$PROD_ID" "$STATE" "$MSG"
done

echo "------------------------------------------------------------------------------------"

# ── 10. DB Dump ───────────────────────────────────────────────
echo ""
printf "${C_BOLD}====================================================\n"
printf "           DETAILED DATABASE TABLES                 \n"
printf "====================================================\n${C_RESET}"
info "Fetching and formatting table data..."

echo "" >> "$SCRIPT_DIR/ms.log"
echo "====================================================" >> "$SCRIPT_DIR/ms.log"
echo "           VISUAL DATABASE REPORT                   " >> "$SCRIPT_DIR/ms.log"
echo "====================================================" >> "$SCRIPT_DIR/ms.log"

curl -s "$MS_URL/api/v1/debug/db/dump" > "$SCRIPT_DIR/db_dump.json"
cleanup
cat "$SCRIPT_DIR/db_dump.json" | python3 "$SCRIPT_DIR/format_tables.py" >> "$SCRIPT_DIR/ms.log"
rm -f "$SCRIPT_DIR/db_dump.json"

info "Detailed tables written to: testing/mocks/ms.log"

# ── 11. Final Validation Summary ─────────────────────────────
echo ""
printf "${C_BOLD}====================================================\n"
printf "         CONTROL ENDPOINTS VALIDATION SUMMARY       \n"
printf "====================================================\n${C_RESET}"

if [ "$VALIDATION_ERRORS" -eq 0 ]; then
    printf "${C_GREEN}${C_BOLD}  ✔ ALL CONTROL ENDPOINTS PASSED${C_RESET}\n"
else
    printf "${C_RED}${C_BOLD}  ✘ $VALIDATION_ERRORS CONTROL ENDPOINT(S) FAILED${C_RESET}\n"
fi
printf "====================================================\n"

exit "$VALIDATION_ERRORS"

