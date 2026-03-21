#!/bin/bash
# Retry wrapper for commands that may fail due to transient infrastructure issues.
# Usage: retry.sh <max_attempts> <command...>

max_attempts=$1
shift

for attempt in $(seq 1 "$max_attempts"); do
  echo "Attempt $attempt of $max_attempts"
  if "$@"; then
    exit 0
  fi
  echo "Attempt $attempt failed"
  if [ "$attempt" -lt "$max_attempts" ]; then
    echo "Retrying in 10 seconds..."
    sleep 10
  fi
done
exit 1
