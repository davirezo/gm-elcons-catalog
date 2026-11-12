<#
.SYNOPSIS
  Backup do PostgreSQL do Docker Compose (pg_dump, formato custom).
.DESCRIPTION
  Gera backups/gmelcons-AAAAMMDD-HHMMSS.dump. O dump é criado DENTRO do container e depois copiado
  com "docker compose cp" (redirecionar a saída no PowerShell com ">" corromperia o arquivo binário).
  Uso (na raiz do projeto):  .\scripts\backup.ps1
#>
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

$running = @(docker compose ps --status running --services)
if ($running -notcontains 'db') {
    Write-Error "O serviço 'db' não está rodando. Suba com: docker compose up -d"
    exit 1
}

New-Item -ItemType Directory -Force -Path backups | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$file = "backups/gmelcons-$stamp.dump"

# As variáveis POSTGRES_USER / POSTGRES_DB já existem dentro do container, não é preciso ler o .env.
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/backup.dump'
if ($LASTEXITCODE -ne 0) { Write-Error 'pg_dump falhou.'; exit 1 }

# Confere se o arquivo gerado é um dump válido antes de copiá-lo.
docker compose exec -T db sh -c 'pg_restore -l /tmp/backup.dump > /dev/null'
if ($LASTEXITCODE -ne 0) { Write-Error 'O dump gerado é inválido.'; exit 1 }

docker compose cp db:/tmp/backup.dump $file
if ($LASTEXITCODE -ne 0) { Write-Error 'Falha ao copiar o dump para o host.'; exit 1 }
docker compose exec -T db rm -f /tmp/backup.dump | Out-Null

$size = [math]::Round((Get-Item $file).Length / 1KB, 1)
Write-Host "Backup criado: $file ($size KB)"
Write-Host "Guarde uma cópia fora desta máquina e com acesso restrito: o dump contém hashes de senha e a auditoria."
