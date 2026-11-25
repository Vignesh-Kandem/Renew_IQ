param([switch]$NonInteractive)

$ErrorActionPreference = 'Stop'
$backendPort = 8081
if ($env:SERVER_PORT) {
    $parsedPort = 0
    if (-not [int]::TryParse($env:SERVER_PORT, [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535) {
        throw 'SERVER_PORT must be a number between 1 and 65535.'
    }
    $backendPort = $parsedPort
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $backendPort -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($listener) {
    throw "Port $backendPort is already in use by process $($listener.OwningProcess). Stop the existing backend with Ctrl+C in its terminal, then run this script again. Run only one backend instance."
}

if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) {
    throw 'Maven was not found. Add the Maven bin directory to PATH and reopen your terminal.'
}
foreach ($requiredVariable in @('SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($requiredVariable))) {
        if ($NonInteractive) {
            throw "$requiredVariable is required. Set it in this terminal before starting the backend; see README.md."
        }
        if ($requiredVariable -eq 'SPRING_DATASOURCE_PASSWORD') {
            $securePassword = Read-Host 'MySQL password (hidden; used only in this terminal session)' -AsSecureString
            try {
                $env:SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
            } finally {
                $securePassword.Dispose()
            }
        } else {
            $env:SPRING_DATASOURCE_USERNAME = (Read-Host 'MySQL username').Trim()
        }
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($requiredVariable))) {
            throw "$requiredVariable cannot be empty. Run the launcher again and enter your MySQL credentials."
        }
    }
}
if (-not $env:AI_API_KEY -and -not $env:GROQ_API_KEY) {
    Write-Warning 'GROQ_API_KEY (or AI_API_KEY) is not set. The app and document library will start, but AI chat requires this key. Set it in this terminal before starting the backend.'
}

Push-Location (Join-Path $PSScriptRoot 'backend')
try {
    $startupLog = Join-Path (Get-Location) 'startup.log'
    # Keep the complete Spring error, which appears before Maven's generic summary.
    $previousErrorAction = $ErrorActionPreference
    try {
        # Windows PowerShell represents native stderr as errors. Keep reading it
        # so Java warnings cannot bypass the diagnostic block below.
        $ErrorActionPreference = 'Continue'
        & mvn.cmd --batch-mode spring-boot:run 2>&1 | Tee-Object -FilePath $startupLog
        $backendExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($backendExitCode -ne 0) {
        $diagnosticLines = @(Select-String -LiteralPath $startupLog -Pattern 'Caused by:|^Description:|^Action:|Port .*already in use|Access denied for user|Communications link failure|Migration .*failed|Validate failed|Schema-validation:|Could not resolve placeholder' | Select-Object -Last 12)
        Write-Host "`nBackend startup diagnostic:" -ForegroundColor Yellow
        if ($diagnosticLines.Count) {
            foreach ($diagnosticLine in $diagnosticLines) {
                $diagnosticText = $diagnosticLine.Line
                foreach ($secretName in @('SPRING_DATASOURCE_PASSWORD', 'AI_API_KEY', 'GROQ_API_KEY')) {
                    $secretValue = [Environment]::GetEnvironmentVariable($secretName)
                    if (-not [string]::IsNullOrEmpty($secretValue)) {
                        $diagnosticText = $diagnosticText.Replace($secretValue, '[REDACTED]')
                    }
                }
                Write-Host $diagnosticText
            }
        } else {
            Write-Host 'See the APPLICATION FAILED TO START section in the log.'
        }
        throw "Backend startup failed. Full output is in $startupLog. Share the diagnostic above, not credentials."
    }
} finally {
    Pop-Location
}
