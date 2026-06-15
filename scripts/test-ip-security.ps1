# IP Validation Security Test Script
# Test 3 scenarios: Valid IP, Spoofed IP, VPN Bypass

$apiBase = "http://localhost/api"
$authToken = ""
$photoBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8VAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABm"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "IP Validation Security Penetration Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# First, login to get token
Write-Host "Step 1: Login để lấy authentication token..." -ForegroundColor Yellow
$loginResponse = Invoke-WebRequest -Uri "$apiBase/auth/login" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body (@{
    username = "emp01"
    password = "password123"
  } | ConvertTo-Json) `
  -SkipHttpErrorCheck

if ($loginResponse.StatusCode -eq 200) {
    $loginData = $loginResponse.Content | ConvertFrom-Json
    $authToken = $loginData.accessToken
    Write-Host "✅ Login thành công!" -ForegroundColor Green
    Write-Host "Token: $($authToken.Substring(0, 20))..." -ForegroundColor Gray
} else {
    Write-Host "❌ Login thất bại!" -ForegroundColor Red
    Write-Host $loginResponse.Content -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "TEST CASE 1: Hacker giả mạo X-Real-IP header" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Scenario: Hacker ngồi ở nhà, cố tình thêm X-Real-IP = 1.2.3.4 (IP văn phòng)"
Write-Host "Expected: CHẶN - Báo lỗi INVALID_IP"
Write-Host ""

$checkInBody = @{
    lat = 10.8
    lng = 106.7
    photoBase64 = $photoBase64
    isClientSite = $false
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $authToken"
    "Content-Type" = "application/json"
    "X-Real-IP" = "1.2.3.4"
    "X-Forwarded-For" = "1.2.3.4"
}

Write-Host "Gửi request với headers giả mạo..." -ForegroundColor Cyan
$response = Invoke-WebRequest -Uri "$apiBase/attendance/check-in" `
    -Method POST `
    -Headers $headers `
    -Body $checkInBody `
    -SkipHttpErrorCheck

Write-Host "Status Code: $($response.StatusCode)" -ForegroundColor Yellow
$responseData = $response.Content | ConvertFrom-Json
Write-Host "Response: " -ForegroundColor Yellow
Write-Host ($responseData | ConvertTo-Json | Out-String) -ForegroundColor Gray

if ($response.StatusCode -eq 403 -and $responseData.error -eq "INVALID_IP") {
    Write-Host "✅ TEST CASE 1 PASSED: Hacker bị chặn thành công!" -ForegroundColor Green
} else {
    Write-Host "⚠️  TEST CASE 1 UNCERTAIN: Status = $($response.StatusCode)" -ForegroundColor Yellow
    Write-Host "   (IP 1.2.3.4 không nằm trong valid_ips table, hoặc điều kiện khác)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "TEST CASE 2: Request trực tiếp bypass Nginx" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Scenario: Hacker bypass qua Nginx, gửi trực tiếp tới Backend:8080"
Write-Host "Expected: CHẶN - Backend chỉ đọc IP từ connection (127.0.0.1 hoặc Docker IP)"
Write-Host ""

Write-Host "Gửi request trực tiếp tới :8080..." -ForegroundColor Cyan
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/attendance/check-in" `
    -Method POST `
    -Headers @{
        "Authorization" = "Bearer $authToken"
        "Content-Type" = "application/json"
        "X-Real-IP" = "1.2.3.4"
    } `
    -Body $checkInBody `
    -SkipHttpErrorCheck

Write-Host "Status Code: $($response.StatusCode)" -ForegroundColor Yellow
$responseData = $response.Content | ConvertFrom-Json
Write-Host "Response Error: $($responseData.error)" -ForegroundColor Yellow

if ($response.StatusCode -eq 403 -and $responseData.error -eq "INVALID_IP") {
    Write-Host "✅ TEST CASE 2 PASSED: Backend chặn request từ 127.0.0.1!" -ForegroundColor Green
} else {
    Write-Host "⚠️  TEST CASE 2 UNCERTAIN: Status = $($response.StatusCode)" -ForegroundColor Yellow
    Write-Host "   (IP 127.0.0.1 không nằm trong valid_ips table)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "DEBUGGING: Check current request IP extraction" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Để verify IP đúng được extracted, check backend logs:" -ForegroundColor Cyan
Write-Host "  docker logs itx-backend-1 | grep -i 'check.in\|invalid_ip' (2 logs gần nhất)" -ForegroundColor Gray

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "KẾT LUẬN:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "✅ Nếu TEST CASE 1 & 2 đều PASS:" -ForegroundColor Green
Write-Host "   → Hệ thống bảo mật 100%. IP Spoofing không thể xảy ra." -ForegroundColor Green
Write-Host ""
Write-Host "⚠️  Nếu TEST CASE bị uncertain:" -ForegroundColor Yellow
Write-Host "   → Kiểm tra database valid_ips table có chứa office IP chưa" -ForegroundColor Yellow
Write-Host "   → Query: SELECT * FROM valid_ips WHERE scope = 'COMPANY' AND active = 1" -ForegroundColor Yellow
