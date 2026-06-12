# 🧪 Frontend IP Validation Testing Guide

## Quy trình Testing

```
Admin thêm Valid IP
        ↓
    [Postman/curl]
        ↓
Employee đăng nhập
        ↓
    [Frontend: http://localhost]
        ↓
Employee check-in tại "văn phòng" (127.0.0.1)
        ↓
    [Frontend + Backend logs]
        ↓
Verify: Check-in SUCCESS ✅
```

---

## Step 1: Admin thêm Valid Office IP

### Option A: Sử dụng Postman/Thunder Client

**1. Lấy Admin Token**

```http
POST http://localhost/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response**:
```json
{
  "accessToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

Lưu `accessToken` để sử dụng ở bước tiếp theo.

---

**2. Admin thêm Valid IP (COMPANY scope)**

```http
POST http://localhost/api/admin/valid-ips
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "ipAddress": "127.0.0.1",
  "scope": "COMPANY",
  "description": "Localhost for testing (office)"
}
```

**Response** (201 Created):
```json
{
  "id": 1,
  "ipAddress": "127.0.0.1",
  "scope": "COMPANY",
  "employeeId": null,
  "description": "Localhost for testing (office)",
  "isActive": true,
  "createdAt": "2026-06-12T02:XX:XXZ"
}
```

✅ **Confirm**: Valid IP đã được thêm vào database

---

### Option B: Trực tiếp via SQL

```bash
docker exec itx-mysql-1 mysql -h localhost -u root -ppassword itx_attendance -e "
INSERT INTO valid_ips (ip_address, scope, description, created_by, is_active) 
VALUES ('127.0.0.1', 'COMPANY', 'Localhost for testing', 'd69d6ef7-6607-11f1-8656-d6c647740448', 1);
SELECT * FROM valid_ips WHERE ip_address = '127.0.0.1';
"
```

---

## Step 2: Start Frontend Dev Server

```bash
cd C:\Users\Admin\OneDrive\Desktop\WorkSpaces\ITX\frontend
npm install
npm run dev
```

**Expected Output**:
```
  VITE v5.x.x  ready in XXX ms

  ➜  Local:   http://localhost:5173
  ➜  press h + enter to show help
```

---

## Step 3: Employee Login & Check-in

### 3A. Open Frontend

- Open browser: **http://localhost** (Nginx reverse proxy)
  - or **http://localhost:5173** (Direct Vite dev server)

### 3B. Login as Employee

**Page**: Login page  
**Username**: `emp01` (test employee from database)  
**Password**: `password123`

**Expected**:
- ✅ Login successful
- ✅ Redirect to Check-in page
- ✅ Shows shift information

---

### 3C. Check-in Process

**Page**: Check-in page (http://localhost/attendance/check-in)

**UI Steps**:

1. **Camera Permission**
   - Browser sẽ yêu cầu cấp quyền camera
   - Click "Allow" để cho phép

2. **Chụp ảnh**
   - Click "Chụp ảnh" button
   - Selfie sẽ được capture (nếu có camera)
   - Nếu không có camera → sử dụng placeholder từ browser DevTools

3. **Select Check-in Mode**
   - ✅ Keep default: "Văn phòng" mode (isClientSite: false)
   - This will require IP validation (vs. client site which requires GPS)

4. **Xác nhận Check-in**
   - Click "Xác nhận check-in" button

---

## Step 4: Verify Success

### 4A: Frontend UI

**Expected Result**:
```
✅ "Check-in thành công!"
   (Toast notification appears)

Check-in lúc: 14:35
Status: ON_TIME (hoặc LATE_IN tùy shift)
```

---

### 4B: Backend Logs

**Monitor logs in real-time**:

```bash
docker logs -f itx-backend-1 | grep -E "IP extracted|check.in|INVALID_IP"
```

**Expected logs**:

```
2026-06-12T02:XX:XX.XXXZ DEBUG [...] IP Extraction Debug: 
  X-Real-IP=127.0.0.1, X-Forwarded-For=127.0.0.1, Remote-Addr=172.x.x.x

2026-06-12T02:XX:XX.XXXZ INFO [...] Client IP extracted and normalized: 127.0.0.1

2026-06-12T02:XX:XX.XXXZ INFO [...] Check-in successful for emp01
```

---

### 4C: Database Verification

**Check attendance record**:

```bash
docker exec itx-mysql-1 mysql -h localhost -u root -ppassword itx_attendance -e "
SELECT 
  ar.employee_id,
  ar.check_in_time,
  ar.check_in_ip,
  ar.client_site,
  ar.attendance_status
FROM attendance_records ar
WHERE ar.date = CURDATE()
ORDER BY ar.check_in_time DESC
LIMIT 5;
"
```

**Expected Output**:
```
employee_id | check_in_time         | check_in_ip | client_site | attendance_status
emp-001     | 2026-06-12 14:35:00   | 127.0.0.1   | 0           | ON_TIME
```

✅ Confirm: `check_in_ip = 127.0.0.1` (correct extracted IP)

---

## Step 5: Test Error Cases (Optional)

### 5A: Check-in from "invalid" IP

**Scenario**: Simulate check-in from outside office (using direct :8080 + fake header)

```bash
# Fake check-in with wrong IP
curl -X POST http://localhost:8080/api/attendance/check-in \
  -H "Authorization: Bearer {token}" \
  -H "X-Real-IP: 1.2.3.4" \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 10.8,
    "lng": 106.7,
    "photoBase64": "data:image/jpeg;base64,/9j/4AAQSkZJ...",
    "isClientSite": false
  }'
```

**Expected Error**:
```json
{
  "error": "INVALID_IP",
  "message": "Không nhận diện được mạng văn phòng"
}
```

---

### 5B: Client Site Mode (GPS Required)

**Scenario**: Employee check-in from outside office (requires GPS, not IP)

**Frontend**: Toggle to "Ngoài văn phòng" mode
- ✅ GPS will be captured
- ✅ IP validation skipped
- ✅ Check-in succeeds (no INVALID_IP error)

---

## Step 6: Test Real Office IP (When Available)

### Replace localhost with real office IP

**1. Get office IP**:
```bash
# On any office machine
ipconfig getifaddr en0  # macOS
ipconfig  # Windows
ifconfig  # Linux
```

Example: `203.0.113.50`

**2. Update valid_ips**:
```sql
INSERT INTO valid_ips (ip_address, scope, description, created_by, is_active)
VALUES ('203.0.113.50', 'COMPANY', 'Office Main Network', 'admin-id', 1);
```

**3. Test from office WiFi**:
- Connect office machine to office WiFi
- Open http://localhost (using office IP as gateway)
- Employee check-in should succeed

**4. Verify logs**:
```bash
docker logs -f itx-backend-1 | grep "Client IP extracted"
# Expected: Client IP extracted and normalized: 203.0.113.50
```

---

## Troubleshooting

### Issue: "Không nhận diện được mạng văn phòng"

**Cause 1**: Valid IP not in database
```bash
docker exec itx-mysql-1 mysql -h localhost -u root -ppassword itx_attendance \
  -e "SELECT ip_address FROM valid_ips WHERE is_active = 1"
```
**Fix**: Add the IP using POST /api/admin/valid-ips

---

**Cause 2**: Wrong IP extracted
```bash
# Check what IP was extracted
docker logs itx-backend-1 | grep "IP extracted" | tail -5
```
**Fix**: 
- If `127.0.0.1` but not in database: Add it
- If different IP: Update database with correct IP

---

**Issue**: Camera not working

**Cause**: Browser permission denied
```bash
# Allow camera in browser settings
# Or use Postman with dummy photoBase64
```

---

**Issue**: Check-out showing "Không tìm thấy bản ghi check-in"

**Cause**: Employee already checked out
**Fix**: Check logs - if already checked out today, try with different employee

---

## Full Test Scenario Script

### PowerShell Script for Automated Testing

```powershell
# test-checkin-flow.ps1

$baseUrl = "http://localhost"
$adminUsername = "admin"
$adminPassword = "admin123"
$empUsername = "emp01"
$empPassword = "password123"

# Step 1: Admin Login
Write-Host "Step 1: Admin Login..." -ForegroundColor Cyan
$adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body (@{
    username = $adminUsername
    password = $adminPassword
  } | ConvertTo-Json)

$adminToken = $adminLogin.accessToken
Write-Host "✅ Admin Token: $($adminToken.Substring(0,20))..." -ForegroundColor Green

# Step 2: Admin Add Valid IP
Write-Host "Step 2: Add Valid IP (127.0.0.1)..." -ForegroundColor Cyan
$addIp = Invoke-RestMethod -Uri "$baseUrl/api/admin/valid-ips" `
  -Method POST `
  -Headers @{
    "Authorization" = "Bearer $adminToken"
    "Content-Type" = "application/json"
  } `
  -Body (@{
    ipAddress = "127.0.0.1"
    scope = "COMPANY"
    description = "Localhost test"
  } | ConvertTo-Json)

Write-Host "✅ Valid IP Added: $($addIp.ipAddress)" -ForegroundColor Green

# Step 3: Employee Login
Write-Host "Step 3: Employee Login..." -ForegroundColor Cyan
$empLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body (@{
    username = $empUsername
    password = $empPassword
  } | ConvertTo-Json)

$empToken = $empLogin.accessToken
Write-Host "✅ Employee Token: $($empToken.Substring(0,20))..." -ForegroundColor Green

# Step 4: Employee Check-in
Write-Host "Step 4: Employee Check-in..." -ForegroundColor Cyan
$photoBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8VAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABm"

$checkIn = Invoke-RestMethod -Uri "$baseUrl/api/attendance/check-in" `
  -Method POST `
  -Headers @{
    "Authorization" = "Bearer $empToken"
    "Content-Type" = "application/json"
  } `
  -Body (@{
    lat = 10.8
    lng = 106.7
    photoBase64 = $photoBase64
    isClientSite = $false
  } | ConvertTo-Json) `
  -SkipHttpErrorCheck

if ($LASTEXITCODE -eq 0) {
  Write-Host "✅ Check-in Successful!" -ForegroundColor Green
  Write-Host "Response: " ($checkIn | ConvertTo-Json) -ForegroundColor Green
} else {
  Write-Host "❌ Check-in Failed!" -ForegroundColor Red
  Write-Host "Error: " $checkIn -ForegroundColor Red
}
```

**Run**:
```bash
powershell -ExecutionPolicy Bypass -File test-checkin-flow.ps1
```

---

## Success Criteria Checklist

- [ ] Admin successfully adds Valid IP via API
- [ ] Valid IP appears in database (MySQL)
- [ ] Employee can login to frontend
- [ ] Employee can capture photo (or use placeholder)
- [ ] Check-in button is enabled
- [ ] Click check-in → Success toast message
- [ ] Backend logs show: "Client IP extracted and normalized: 127.0.0.1"
- [ ] Backend logs show: Check-in success
- [ ] Database shows attendance record with correct IP
- [ ] Refresh page → Shows "Check-in lúc: XX:XX" (already checked in)

---

## Next: Test with Actual Office IP

Once verified locally with 127.0.0.1:

1. **Get office IP from IT** (e.g., 203.0.113.50)
2. **Update database**:
   ```sql
   INSERT INTO valid_ips (ip_address, scope, description, created_by, is_active)
   VALUES ('203.0.113.50', 'COMPANY', 'Office Main', 'admin-id', 1);
   ```
3. **Test from office WiFi**:
   - Connect to office WiFi
   - Open http://localhost
   - Employee check-in → Should succeed

---

## 📊 Expected Test Results

| Test Case | Input | Expected | Result |
|-----------|-------|----------|--------|
| Add Valid IP | 127.0.0.1 (COMPANY) | Saved to DB | ✅ |
| Admin Login | admin/admin123 | Token returned | ✅ |
| Employee Login | emp01/password123 | Token returned | ✅ |
| Check-in (Office IP) | lat/lng/photo | Success toast | ✅ |
| Backend Logs | Check after check-in | IP extracted correctly | ✅ |
| Database | Check attendance_records | Record created with correct IP | ✅ |
| Check-in again same day | Try re-checkin | Error: ALREADY_CHECKED_IN | ✅ |

---

## Debugging Commands

```bash
# Monitor backend logs in real-time
docker logs -f itx-backend-1 | grep -i "ip\|checkin"

# Check valid IPs in database
docker exec itx-mysql-1 mysql -h localhost -u root -ppassword itx_attendance \
  -e "SELECT id, ip_address, scope, is_active FROM valid_ips"

# Check today's attendance records
docker exec itx-mysql-1 mysql -h localhost -u root -ppassword itx_attendance \
  -e "SELECT employee_id, check_in_ip, check_in_time, attendance_status FROM attendance_records WHERE date(check_in_time) = CURDATE()"

# Restart backend if needed
docker restart itx-backend-1

# View full backend logs
docker logs itx-backend-1 | tail -100
```

---

✅ **Ready to test!** Follow steps above and report results. Good luck! 🚀
