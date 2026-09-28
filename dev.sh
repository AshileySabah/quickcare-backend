#!/usr/bin/env bash
# Sobe Postgres + back-end no Docker e espera a API responder.
#   ./dev.sh          sobe (reconstruindo a imagem com o codigo atual)
#   ./dev.sh stop     derruba tudo
set -euo pipefail
cd "$(dirname "$0")"

if [ "${1:-}" = "stop" ]; then
  docker compose down
  exit 0
fi

docker compose up -d --build

echo "Aguardando o back-end ficar pronto..."
for _ in $(seq 1 60); do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' quickcare-backend)" = "healthy" ]; then
    echo
    echo "Back-end no ar!"
    echo "  API:     http://localhost:8080"
    echo "  Swagger: http://localhost:8080/swagger-ui/index.html"
    echo "  Logs:    docker compose logs -f backend"
    exit 0
  fi
  sleep 3
done

docker compose logs --tail 50 backend
echo "O back-end nao ficou pronto em 3 minutos (logs acima)." >&2
exit 1
