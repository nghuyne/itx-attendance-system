# Assign Shift to Employee Script

$baseUrl = "http://localhost"
$adminUsername = "admin"
$adminPassword = "admin123"

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Assign Shift to Employee" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Admin Login
Write-Host "Step 1: Admin Login..." -ForegroundColor Yellow
$loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body (@{
    username = $adminUsername
    password = $adminPassword
  } | ConvertTo-Json)

$adminToken = $loginResponse.accessToken
Write-Host "✅ Token: $($adminToken.Substring(0,20))..." -ForegroundColor Green
Write-Host ""

# Step 2: Get Shifts
Write-Host "Step 2: Danh sách Ca làm việc..." -ForegroundColor Yellow
$shiftsResponse = Invoke-RestMethod -Uri "$baseUrl/api/admin/shifts?page=0&size=100" `
  -Method GET `
  -Headers @{"Authorization"="Bearer $adminToken"} `
  -SkipHttpErrorCheck

$shifts = $shiftsResponse.content
if ($shifts.Count -eq 0) {
  Write-Host "❌ Không có ca nào. Vui lòng tạo ca trước." -ForegroundColor Red
  exit 1
}

Write-Host "Danh sách ca:" -ForegroundColor Green
for ($i = 0; $i -lt $shifts.Count; $i++) {
  Write-Host "  [$i] $($shifts[$i].name) ($($shifts[$i].startTime) - $($shifts[$i].endTime))" -ForegroundColor Green
}
Write-Host ""

# Step 3: Get Employees
Write-Host "Step 3: Danh sách Nhân viên..." -ForegroundColor Yellow
$employeesResponse = Invoke-RestMethod -Uri "$baseUrl/api/admin/employees" `
  -Method GET `
  -Headers @{"Authorization"="Bearer $adminToken"} `
  -SkipHttpErrorCheck

if (-not $employeesResponse -or $employeesResponse.Count -eq 0) {
  Write-Host "❌ Không có nhân viên nào." -ForegroundColor Red
  exit 1
}

Write-Host "Danh sách nhân viên:" -ForegroundColor Green
for ($i = 0; $i -lt $employeesResponse.Count; $i++) {
  Write-Host "  [$i] $($employeesResponse[$i].username) (ID: $($employeesResponse[$i].id))" -ForegroundColor Green
}
Write-Host ""

# Step 4: User Input
Write-Host "Step 4: Chọn ca và nhân viên..." -ForegroundColor Yellow
$shiftIndex = Read-Host "Nhập index ca (0-$($shifts.Count-1))"
$employeeIndex = Read-Host "Nhập index nhân viên (0-$($employeesResponse.Count-1))"

if ([int]$shiftIndex -lt 0 -or [int]$shiftIndex -ge $shifts.Count) {
  Write-Host "❌ Index ca không hợp lệ" -ForegroundColor Red
  exit 1
}

if ([int]$employeeIndex -lt 0 -or [int]$employeeIndex -ge $employeesResponse.Count) {
  Write-Host "❌ Index nhân viên không hợp lệ" -ForegroundColor Red
  exit 1
}

$selectedShift = $shifts[[int]$shiftIndex]
$selectedEmployee = $employeesResponse[[int]$employeeIndex]

Write-Host ""
Write-Host "Xác nhận gán ca:" -ForegroundColor Cyan
Write-Host "  Ca: $($selectedShift.name)" -ForegroundColor Cyan
Write-Host "  Nhân viên: $($selectedEmployee.username)" -ForegroundColor Cyan
Write-Host ""

$confirm = Read-Host "Bạn có chắc? (yes/no)"
if ($confirm -ne "yes") {
  Write-Host "Đã hủy" -ForegroundColor Yellow
  exit 0
}

# Step 5: Assign Shift
Write-Host ""
Write-Host "Step 5: Gán ca..." -ForegroundColor Yellow

$assignResponse = Invoke-RestMethod -Uri "$baseUrl/api/admin/shifts/$($selectedShift.id)/assign/$($selectedEmployee.id)" `
  -Method PUT `
  -Headers @{"Authorization"="Bearer $adminToken"} `
  -SkipHttpErrorCheck

if ($assignResponse -and $assignResponse.id) {
  Write-Host "✅ Gán ca thành công!" -ForegroundColor Green
  Write-Host "   Ca: $($assignResponse.name)" -ForegroundColor Green
  Write-Host "   Nhân viên: $($selectedEmployee.username)" -ForegroundColor Green
} else {
  Write-Host "❌ Gán ca thất bại!" -ForegroundColor Red
  Write-Host $assignResponse -ForegroundColor Red
  exit 1
}

Write-Host ""
Write-Host "✅ Xong! Nhân viên sẵn sàng check-in." -ForegroundColor Green
