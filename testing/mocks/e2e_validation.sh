#!/bin/bash

# Configuration
MS_URL="http://localhost:8080"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
MOCKS_PID_FILE="$SCRIPT_DIR/mocks.pid"
MS_PID_FILE="$SCRIPT_DIR/ms.pid"

cleanup() {
    echo "Cleaning up..."
    [ -f "$MOCKS_PID_FILE" ] && kill -9 $(cat "$MOCKS_PID_FILE") 2>/dev/null
    [ -f "$MS_PID_FILE" ] && kill -9 $(cat "$MS_PID_FILE") 2>/dev/null
    /usr/sbin/lsof -ti :8080,3003,9003 | xargs kill -9 2>/dev/null || true
    rm -f "$MOCKS_PID_FILE" "$MS_PID_FILE"
    sleep 2
}

# Kill previous processes if they exist
cleanup

echo "===================================================="
echo "  FILE PROCESSOR - E2E VALIDATION (MULTI-SCENARIO)"
echo "===================================================="

echo "1. Starting Mocks..."
cd "$SCRIPT_DIR"
python3 mocks.py > mocks.log 2>&1 &
echo $! > "$MOCKS_PID_FILE"
sleep 3

echo "2. Starting Microservice (Profile: dev)..."
cd "$ROOT_DIR"
./gradlew bootRun --args='--spring.profiles.active=dev --app.document-rest.endpoint=http://localhost:3003 --app.soap.v2.endpoint=http://localhost:9003/soap/adminDocs' > "$SCRIPT_DIR/ms.log" 2>&1 &
echo $! > "$MS_PID_FILE"

# Wait for MS
until curl -s $MS_URL/actuator/health | grep -q "UP"; do
    printf "."
    sleep 5
done
echo -e "\nMS is UP!"

echo "3. Synchronizing Documents (GET /sync)..."
curl -s -H "use-case: SOAP" "$MS_URL/api/v1/products/sync/soap" > /dev/null
echo "   Waiting for sync to complete..."
until grep -q "Document sync completed" "$SCRIPT_DIR/ms.log"; do
    sleep 2
done
echo "   Sync completed!"

echo "4. Processing Documents - FIRST RUN (GET /products)..."
curl -s "$MS_URL/api/v1/products/soap" > /dev/null
echo "   Waiting for first run to complete..."
until grep -q "Pending documents processing completed" "$SCRIPT_DIR/ms.log"; do
    sleep 2
done
echo "   First run completed! Waiting 5s before next run..."
sleep 5

echo "--- SECOND RUN LOGS START HERE ---" >> "$SCRIPT_DIR/ms.log"

echo "5. Processing Documents - SECOND RUN (GET /products)..."
curl -s "$MS_URL/api/v1/products/soap" > /dev/null
echo "   Waiting for second run to complete..."
until [ $(grep -c "Pending documents processing completed" "$SCRIPT_DIR/ms.log") -ge 2 ]; do
    sleep 2
done
echo "   Second run completed! Waiting 5s for processing to settle..."
sleep 5

echo ""
echo "===================================================="
echo "                TEST RESULTS SUMMARY                "
echo "===================================================="
printf "%-20s | %-15s | %-10s | %s\n" "DOCUMENT ID" "PRODUCT" "RESULT" "MESSAGE"
echo "------------------------------------------------------------------------------------"

# Clear ms.log table section and add header
echo "" >> "$SCRIPT_DIR/ms.log"
echo "====================================================" >> "$SCRIPT_DIR/ms.log"
echo "           VISUAL DATABASE REPORT                   " >> "$SCRIPT_DIR/ms.log"
echo "====================================================" >> "$SCRIPT_DIR/ms.log"

# Parse logs to find results
grep "Document .* (Product: .*)" "$SCRIPT_DIR/ms.log" | grep -v "SUMMARY" | while read -r line; do
    STATUS=$(echo "$line" | sed -E 's/.*\[(SUCCESS|FAILURE|RETRYABLE_ERROR)\].*/\1/')
    DOC_ID=$(echo "$line" | sed -E 's/.*Document ([^ ]+).*/\1/')
    PROD_ID=$(echo "$line" | sed -E 's/.*Product: ([^)]+).*/\1/')
    STATE=$(echo "$line" | sed -E 's/.*-> ([^.]+).*/\1/')
    MSG=$(echo "$line" | sed -E 's/.*Messages: (.*)/\1/')
    
    COLOR_RESET='\033[0m'; COLOR_RED='\033[0;31m'; COLOR_GREEN='\033[0;32m'; COLOR_YELLOW='\033[0;33m'
    FINAL_COLOR=$COLOR_RESET
    [ "$STATUS" == "SUCCESS" ] && FINAL_COLOR=$COLOR_GREEN
    [ "$STATUS" == "FAILURE" ] && FINAL_COLOR=$COLOR_RED
    [ "$STATUS" == "RETRYABLE_ERROR" ] && FINAL_COLOR=$COLOR_YELLOW
    
    printf "%-20s | %-15s | ${FINAL_COLOR}%-10s${COLOR_RESET} | %s\n" "$DOC_ID" "$PROD_ID" "$STATE" "$MSG"
done

echo "------------------------------------------------------------------------------------"
echo "--- E2E VALIDATION COMPLETE ---"

echo ""
echo "===================================================="
echo "           DETAILED DATABASE TABLES                 "
echo "===================================================="
echo "Fetching and formatting table data..."

# Fetch data to a temporary file
curl -s "$MS_URL/api/v1/debug/db/dump" > "$SCRIPT_DIR/db_dump.json"

# Cleanup to stop the microservice from logging concurrently
cleanup

# Format tables and append cleanly
cat "$SCRIPT_DIR/db_dump.json" | python3 "$SCRIPT_DIR/format_tables.py" >> "$SCRIPT_DIR/ms.log"
rm -f "$SCRIPT_DIR/db_dump.json"

echo "Detailed tables with ALL FIELDS have been added to: testing/mocks/ms.log"
echo "You can view them by opening the file or using 'cat testing/mocks/ms.log'"
echo "===================================================="
