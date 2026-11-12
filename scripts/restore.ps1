<#
.SYNOPSIS
  Restaura um backup (.dump) no PostgreSQL do Docker Compose.
.DESCRIPTION
  ATENÇÃO: substitui os dados atuais do banco pelos do backup. A aplicação é parada durante a restauração
  e religada no fim. Pede confirmação digitada antes de começar.
  Uso:  .\scripts\restore.ps1 -File backups\gmelcons-20261111-203000.dump
#>
param(
    [Parameter(Mandatory = $true)][string]$File
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Test-Path $File)) {
    if (Test-Path backups) { Write-Host 'Backups disponíveis:'; Get-ChildItem backups -Filter *.dump | ForEach-Object { "  $($_.Name)" } }
    Write-Error "Arquivo não encontrado: $File"
    exit 1
}
$running = @(docker compose ps --status running --services)
if ($running -notcontains 'db') {
    Write-Error "O serviço 'db' não está rodando. Suba com: docker compose up -d db"
    exit 1
}

Write-Host "Isto vai SUBSTITUIR os dados atuais do banco pelo conteúdo de: $File" -ForegroundColor Yellow
$answer = Read-Host "Digite RESTAURAR para continuar"
if ($answer -ne 'RESTAURAR') { Write-Host 'Cancelado. Nada foi alterado.'; exit 0 }

Write-Host 'Parando a aplicação...'
docker compose stop app | Out-Null

try {
    docker compose cp $File db:/tmp/restore.dump
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar o backup para o container.' }

    # --clean --if-exists: apaga os objetos existentes antes de recriá-los; --no-owner: usa o dono atual.
    docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner /tmp/restore.dump'
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore terminou com erro. Confira a saída acima.' }
    Write-Host 'Restauração concluída.'
}
finally {
    docker compose exec -T db rm -f /tmp/restore.dump | Out-Null
    Write-Host 'Subindo a aplicação...'
    docker compose start app | Out-Null
}
