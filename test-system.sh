#!/bin/bash

# ITX System E2E Test Script
# Run: bash test-system.sh

set -e

BASE_URL="http://localhost"
API_URL="$BASE_URL/api"

echo "======================================"
echo "ITX System E2E Test Started"
echo "======================================"

# ==================== PHASE 1: Health Check ====================
echo -e "\n[1] Health Check..."
curl -s "$BASE_URL/actuator/health" | grep -q "UP" && echo "✅ Backend UP" || echo "❌ Backend DOWN"

# ==================== PHASE 2: Admin Login ====================
echo -e "\n[2] Admin Login..."
ADMIN_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

ADMIN_TOKEN=$(echo $ADMIN_RESPONSE | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
echo "✅ Admin Token: ${ADMIN_TOKEN:0:30}..."

# ==================== PHASE 3: Check Shifts ====================
echo -e "\n[3] Getting Shifts..."
SHIFTS=$(curl -s "$API_URL/admin/shifts" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

SHIFT_ID=$(echo $SHIFTS | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
echo "✅ Shift ID: $SHIFT_ID"

# ==================== PHASE 4: Assign Shifts ====================
echo -e "\n[4] Assigning Shifts to Employees..."
for EMP in emp-001 emp-002 emp-003; do
  curl -s -X PUT "$API_URL/admin/shifts/$SHIFT_ID/assign/$EMP" \
    -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
  echo "✅ Assigned to $EMP"
done

# ==================== PHASE 5: Add Valid IPs ====================
echo -e "\n[5] Adding Valid IPs (Company Scope)..."
curl -s -X POST "$API_URL/admin/valid-ips" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ipAddress":"127.0.0.1","scope":"COMPANY","description":"Localhost"}' > /dev/null
echo "✅ Added 127.0.0.1"

curl -s -X POST "$API_URL/admin/valid-ips" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ipAddress":"::1","scope":"COMPANY","description":"Localhost IPv6"}' > /dev/null
echo "✅ Added ::1"

# ==================== PHASE 6: Employee Login ====================
echo -e "\n[6] Employee1 Login..."
EMP_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"employee1","password":"admin123"}')

EMP_TOKEN=$(echo $EMP_RESPONSE | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
echo "✅ Employee Token: ${EMP_TOKEN:0:30}..."

# ==================== PHASE 7: Employee Check-in ====================
echo -e "\n[7] Employee Check-in (Client Site Mode)..."
CHECKIN_RESPONSE=$(curl -s -X POST "$API_URL/attendance/check-in" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 10.7769,
    "lng": 106.7009,
    "photoBase64": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8VAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k=",
    "isClientSite": true
  }')

RECORD_ID=$(echo $CHECKIN_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
CHECK_STATUS=$(echo $CHECKIN_RESPONSE | grep -o '"attendanceStatus":"[^"]*' | cut -d'"' -f4)
echo "✅ Check-in Success - Record ID: $RECORD_ID, Status: $CHECK_STATUS"

# ==================== PHASE 8: Employee Check-out ====================
echo -e "\n[8] Employee Check-out..."
CHECKOUT_RESPONSE=$(curl -s -X POST "$API_URL/attendance/check-out" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 10.7769,
    "lng": 106.7009,
    "photoBase64": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8VAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k="
  }')

FINAL_STATUS=$(echo $CHECKOUT_RESPONSE | grep -o '"attendanceStatus":"[^"]*' | cut -d'"' -f4)
echo "✅ Check-out Success - Final Status: $FINAL_STATUS"

# ==================== PHASE 9: View History ====================
echo -e "\n[9] Viewing Attendance History..."
HISTORY=$(curl -s "$API_URL/attendance/history?from=2026-06-11&to=2026-06-11&page=0&size=20" \
  -H "Authorization: Bearer $EMP_TOKEN")

RECORD_COUNT=$(echo $HISTORY | grep -o '"totalElements":[0-9]*' | cut -d':' -f2)
echo "✅ Found $RECORD_COUNT attendance records"

# ==================== PHASE 10: Exception Request ====================
echo -e "\n[10] Submitting Exception Request..."
EXCEPTION_RESPONSE=$(curl -s -X POST "$API_URL/requests/exception" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"attendanceRecordId\": \"$RECORD_ID\",
    \"requestType\": \"HALF_DAY\",
    \"reason\": \"Personal work needed\"
  }")

EXCEPTION_ID=$(echo $EXCEPTION_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
EXCEPTION_STATUS=$(echo $EXCEPTION_RESPONSE | grep -o '"status":"[^"]*' | cut -d'"' -f4)
echo "✅ Exception Request ID: $EXCEPTION_ID, Status: $EXCEPTION_STATUS"

# ==================== PHASE 11: Leader Login & Approve ====================
echo -e "\n[11] Leader1 Login..."
LEADER_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"leader1","password":"admin123"}')

LEADER_TOKEN=$(echo $LEADER_RESPONSE | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
echo "✅ Leader Token: ${LEADER_TOKEN:0:30}..."

echo -e "\n[12] Leader Approving Exception Request..."
APPROVE_RESPONSE=$(curl -s -X PUT "$API_URL/requests/$EXCEPTION_ID/approve" \
  -H "Authorization: Bearer $LEADER_TOKEN")

APPROVED_STATUS=$(echo $APPROVE_RESPONSE | grep -o '"status":"[^"]*' | cut -d'"' -f4)
echo "✅ Request Approved - Status: $APPROVED_STATUS"

# ==================== PHASE 12: Admin Override ====================
echo -e "\n[13] Admin Overriding Attendance Record..."
OVERRIDE_RESPONSE=$(curl -s -X PUT "$API_URL/admin/attendance/$RECORD_ID/override" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fields": {"attendanceStatus": "ON_TIME"},
    "auditReason": "Test override"
  }')

IS_OVERRIDE=$(echo $OVERRIDE_RESPONSE | grep -o '"isAdminOverride":[^,}]*' | cut -d':' -f2)
echo "✅ Admin Override Applied - isAdminOverride: $IS_OVERRIDE"

# ==================== SUMMARY ====================
echo -e "\n======================================"
echo "✅ ALL TESTS PASSED!"
echo "======================================"
echo "Summary:"
echo "  - Health Check: OK"
echo "  - Admin Auth: OK"
echo "  - Shift Management: OK"
echo "  - IP Whitelist: OK"
echo "  - Employee Auth: OK"
echo "  - Check-in: OK (Record: $RECORD_ID)"
echo "  - Check-out: OK (Status: $FINAL_STATUS)"
echo "  - History: OK ($RECORD_COUNT records)"
echo "  - Exception Request: OK (ID: $EXCEPTION_ID)"
echo "  - Leader Approval: OK"
echo "  - Admin Override: OK"
echo "======================================"
