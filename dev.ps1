# Sobe Postgres + back-end no Docker e espera a API responder.
#   .\dev.ps1          sobe (reconstruindo a imagem com o codigo atual)
#   .\dev.ps1 -Stop    derruba tudo
param([switch]$Stop)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if ($Stop) {
    docker compose down
    return
}

$backendRunning = (docker ps -q -f name=^quickcare-backend$) -ne $null
$portOwner = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portOwner -and -not $backendRunning) {
    $process = Get-Process -Id $portOwner.OwningProcess
    throw "A porta 8080 ja esta em uso por '$($process.ProcessName)' (PID $($process.Id)). Pare esse processo (ex.: back-end rodando pela IDE) e rode de novo."
}

docker compose up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Falha ao subir os containers.' }

Write-Host 'Aguardando o back-end ficar pronto...'
$deadline = (Get-Date).AddMinutes(3)
while ((docker inspect -f '{{.State.Health.Status}}' quickcare-backend) -ne 'healthy') {
    if ((Get-Date) -gt $deadline) {
        docker compose logs --tail 50 backend
        throw 'O back-end nao ficou pronto em 3 minutos (logs acima).'
    }
    Start-Sleep -Seconds 3
}

Write-Host ''
Write-Host 'Back-end no ar!' -ForegroundColor Green
Write-Host '  API:     http://localhost:8080'
Write-Host '  Swagger: http://localhost:8080/swagger-ui/index.html'
Write-Host '  Logs:    docker compose logs -f backend'
