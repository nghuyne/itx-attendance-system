$ErrorActionPreference = 'Stop'
$loginBody = @{
    username = 'employee2'
    password = 'admin123'
} | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -Body $loginBody -ContentType 'application/json'
$token = $loginRes.accessToken
$headers = @{ Authorization = "Bearer $token" }
try {
    $res = Invoke-RestMethod -Uri 'http://localhost:8080/api/requests/me' -Headers $headers
    $res | ConvertTo-Json -Depth 10
} catch {
    $err = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($err)
    Write-Host "ERROR RESPONSE:"
    $reader.ReadToEnd()
}
