#!/usr/bin/env bash
# Restaura um backup (.dump) no PostgreSQL do Docker Compose.
# ATENÇÃO: substitui os dados atuais do banco. A aplicação é parada durante a restauração.
# Uso:  ./scripts/restore.sh backups/gmelcons-20261111-203000.dump
set -euo pipefail
cd "$(dirname "$0")/.."

file="${1:-}"
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "Uso: $0 <arquivo.dump>" >&2
  ls backups/*.dump 2>/dev/null | sed 's/^/  /' >&2 || true
  exit 1
fi
if ! docker compose ps --status running --services | grep -qx db; then
  echo "O serviço 'db' não está rodando. Suba com: docker compose up -d db" >&2
  exit 1
fi

echo "Isto vai SUBSTITUIR os dados atuais do banco pelo conteúdo de: $file"
read -r -p "Digite RESTAURAR para continuar: " answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado. Nada foi alterado."; exit 0; }

docker compose stop app
trap 'docker compose exec -T db rm -f /tmp/restore.dump || true; docker compose start app' EXIT

docker compose cp "$file" db:/tmp/restore.dump
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner /tmp/restore.dump'
echo "Restauração concluída."
