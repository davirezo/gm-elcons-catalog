#!/usr/bin/env bash
# Backup do PostgreSQL do Docker Compose (pg_dump, formato custom).
# Uso (na raiz do projeto):  ./scripts/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if ! docker compose ps --status running --services | grep -qx db; then
  echo "O serviço 'db' não está rodando. Suba com: docker compose up -d" >&2
  exit 1
fi

mkdir -p backups
file="backups/gmelcons-$(date +%Y%m%d-%H%M%S).dump"

docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/backup.dump'
docker compose exec -T db sh -c 'pg_restore -l /tmp/backup.dump > /dev/null'   # confere se o dump é válido
docker compose cp db:/tmp/backup.dump "$file"
docker compose exec -T db rm -f /tmp/backup.dump

echo "Backup criado: $file ($(du -h "$file" | cut -f1))"
echo "Guarde uma cópia fora desta máquina e com acesso restrito: o dump contém hashes de senha e a auditoria."
