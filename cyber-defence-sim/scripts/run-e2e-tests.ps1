param(
    [string]$BaseHost = "localhost",
    [string]$ServiceJwtSecret = "local-service-jwt-secret-change-me-now",
    [string]$OperatorPassword = "local-operator-change-me",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-ServiceJwt([string]$Subject, [string]$Role, [string]$Audience) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = @{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress
    $payload = @{
        iss = "cybersim-services-local"
        sub = $Subject
        aud = @($Audience)
        iat = $now
        exp = $now + 120
        roles = @($Role)
        token_type = "service"
    } | ConvertTo-Json -Compress
    $encodedHeader = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
    $encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $unsigned = "$encodedHeader.$encodedPayload"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    try {
        $hmac.Key = [Text.Encoding]::UTF8.GetBytes($ServiceJwtSecret)
        $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    }
    finally {
        $hmac.Dispose()
    }
    return "$unsigned.$signature"
}

function Read-Json([string]$Uri) {
    return Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 5
}

function Wait-ForHealthyService([int]$Port, [datetime]$Deadline) {
    $uri = "http://${BaseHost}:$Port/actuator/health"
    do {
        try {
            $health = Read-Json $uri
            if ($health.status -eq "UP") {
                return
            }
        }
        catch {
            # A container may accept connections before its application is ready.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $Deadline)

    throw "Service on port $Port did not become healthy before the timeout."
}

$requiredPorts = 8101, 8103, 8104, 8105, 8107, 8109, 8110, 8111, 8112, 8113, 8114
$healthDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
foreach ($port in $requiredPorts) {
    Wait-ForHealthyService $port $healthDeadline
}

$loginRequest = @{ username = "demo-operator"; password = $OperatorPassword } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://${BaseHost}:8101/auth/login" `
    -ContentType "application/json" -Body $loginRequest
$userHeaders = @{ Authorization = "Bearer $($login.accessToken)" }

$patchNames = @(
    "auth-required",
    "object-authorization",
    "rate-limit",
    "disable-debug-endpoint",
    "input-validation",
    "update-dependency-metadata"
)
foreach ($patchName in $patchNames) {
    $resetToken = New-ServiceJwt "e2e-reset" "SERVICE_REMEDIATION" "target-system-service"
    Invoke-RestMethod -Method Post `
        -Uri "http://${BaseHost}:8104/internal/patches/$patchName/rollback" `
        -Headers @{ Authorization = "Bearer $resetToken" } | Out-Null
}

$request = @{
    name = "Compose End-to-End Simulation"
    mode = "INTERNAL_SANDBOX"
    targetId = "00000000-0000-0000-0000-000000000101"
    maxRounds = 1
    maxDurationMinutes = 15
    stopWhenNoNewFindingsForRounds = 1
} | ConvertTo-Json

$simulation = Invoke-RestMethod -Method Post -Uri "http://${BaseHost}:8105/simulations" `
    -Headers $userHeaders -ContentType "application/json" -Body $request
$round = Invoke-RestMethod -Uri "http://${BaseHost}:8105/simulations/$($simulation.id)/rounds" `
    -Headers $userHeaders | Select-Object -First 1
$controlToken = New-ServiceJwt "e2e-orchestrator-control" "SERVICE_ORCHESTRATOR_CONTROL" "simulation-orchestrator-service"
Invoke-RestMethod -Method Post `
    -Uri "http://${BaseHost}:8105/simulations/$($simulation.id)/rounds/$($round.id)/advance" `
    -Headers @{ Authorization = "Bearer $controlToken" } | Out-Null

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    $simulation = Invoke-RestMethod -Uri "http://${BaseHost}:8105/simulations/$($simulation.id)" -Headers $userHeaders
    $round = Invoke-RestMethod -Uri "http://${BaseHost}:8105/simulations/$($simulation.id)/rounds" `
        -Headers $userHeaders | Select-Object -First 1
} while ($round.status -notin @("COMPLETED", "FAILED") -and (Get-Date) -lt $deadline)

$findings = Read-Json "http://${BaseHost}:8109/simulations/$($simulation.id)/vulnerabilities"
$detections = Read-Json "http://${BaseHost}:8110/simulations/$($simulation.id)/detections"
$remediations = Read-Json "http://${BaseHost}:8111/simulations/$($simulation.id)/remediations"
$verifications = Read-Json "http://${BaseHost}:8112/simulations/$($simulation.id)/verifications"
$scores = Read-Json "http://${BaseHost}:8113/simulations/$($simulation.id)/scores"
$scoreEvents = Read-Json "http://${BaseHost}:8113/simulations/$($simulation.id)/score-events"

$failures = @()
if ($simulation.status -ne "COMPLETED" -or $round.status -ne "COMPLETED") { $failures += "round did not complete" }
if ($findings.Count -ne 6 -or @($findings | Where-Object status -ne "VERIFIED").Count -ne 0) { $failures += "findings" }
if ($detections.Count -ne 6) { $failures += "detections" }
if ($remediations.Count -ne 6 -or @($remediations | Where-Object status -ne "VERIFIED").Count -ne 0) { $failures += "remediations" }
if ($verifications.Count -ne 6 -or @($verifications | Where-Object status -ne "PASSED").Count -ne 0) { $failures += "verifications" }
if ($scoreEvents.Count -ne 36 -or $scores.finalRiskScore -ne 0) { $failures += "scoring" }
if ($failures.Count -gt 0) {
    throw "End-to-end assertions failed: $($failures -join ', ') " +
        "(findings=$($findings.Count), detections=$($detections.Count), remediations=$($remediations.Count), " +
        "verifications=$($verifications.Count), scoreEvents=$($scoreEvents.Count), risk=$($scores.finalRiskScore))"
}

Write-Output "PASS simulation=$($simulation.id) findings=6 detections=6 remediations=6 verifications=6 scoreEvents=36 risk=0"
