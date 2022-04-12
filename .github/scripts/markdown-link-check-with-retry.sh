#!/bin/bash -e

for file in "$@"; do
  for i in 1 2 3; do
    if markdown-link-check --config "$(dirname "$0")/markdown-link-check-config.json" \
                           "$file"; then
      break
    elif [[ $i == 3 ]]; then
      exit 1
    fi
    sleep 5
  done
done
