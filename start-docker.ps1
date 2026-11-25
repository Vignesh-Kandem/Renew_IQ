param()
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Install Docker Desktop with Linux containers, start it, then run this script again.'
}
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker is not running. Start Docker Desktop with Linux containers.' }
Push-Location $PSScriptRoot
try {
    $environmentFile = Join-Path $PSScriptRoot '.env'
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        # Hex avoids Compose interpolation and shell quoting problems.
        $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $secretBytes = New-Object byte[] 32
        try {
            $generator.GetBytes($secretBytes)
            $databaseSecret = [BitConverter]::ToString($secretBytes).Replace('-', '')
            $generator.GetBytes($secretBytes)
            $rootSecret = [BitConverter]::ToString($secretBytes).Replace('-', '')
        } finally { $generator.Dispose() }
        @("MYSQL_PASSWORD=$databaseSecret", "MYSQL_ROOT_PASSWORD=$rootSecret", 'APP_PORT=8080', 'REMINDER_EMAIL_ENABLED=false') |
            Set-Content -LiteralPath $environmentFile -Encoding ascii
        Write-Host 'Created ignored .env with random local database credentials.'
    }
    & docker compose up --build --detach --wait --wait-timeout 600
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. Inspect docker compose logs; existing database data was retained.' }
    Write-Host 'RenewIQ is ready. Default URL: http://localhost:8080 (or APP_PORT configured in .env).'
} finally { Pop-Location }
