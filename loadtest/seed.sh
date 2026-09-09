#!/usr/bin/env bash
# Seeds N short codes against a running instance so redirect_workload.lua
# has real codes to hammer.
#
# Usage: ./loadtest/seed.sh [host] [count]
set -euo pipefail

HOST="${1:-http://localhost:8080}"
COUNT="${2:-5000}"
OUT="$(dirname "$0")/codes.txt"

echo "Seeding $COUNT short URLs against $HOST ..."
: > "$OUT"

for i in $(seq 1 "$COUNT"); do
    CODE=$(curl -s -X POST "$HOST/api/v1/shorten" \
        -H "Content-Type: application/json" \
        -d "{\"longUrl\":\"https://example.com/seed/$i\"}" \
        | grep -o '"shortCode":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$CODE" ]; then
        echo "$CODE" >> "$OUT"
    fi
    if (( i % 500 == 0 )); then
        echo "  seeded $i/$COUNT"
    fi
done

echo "Done. $(wc -l < "$OUT") codes written to $OUT"
