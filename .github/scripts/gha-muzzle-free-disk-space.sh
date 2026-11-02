#!/bin/bash
set -euo pipefail

if (( $# != 0 )); then
  echo "This Muzzle preparation script takes no arguments." >&2
  exit 2
fi

# Reserve room for restored Gradle state, newly resolved dependencies and build outputs.
minimum_bytes=$((50 * 1024 * 1024 * 1024))
available_bytes=$(python3 -c 'import shutil; print(shutil.disk_usage(".").free)')

if (( available_bytes >= minimum_bytes )); then
  echo "MUZZLE_DISK_PREPARATION decision=skip available_bytes=$available_bytes threshold_bytes=$minimum_bytes"
else
  echo "MUZZLE_DISK_PREPARATION decision=cleanup available_bytes=$available_bytes threshold_bytes=$minimum_bytes"
  bash -e .github/scripts/gha-free-disk-space.sh
fi
