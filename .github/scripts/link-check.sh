#!/bin/bash

set -e

export MSYS_NO_PATHCONV=1 # for Git Bash on Windows

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LYCHEE_CONFIG="$SCRIPT_DIR/../../.lychee.toml"
DEPENDENCIES_DOCKERFILE="$SCRIPT_DIR/dependencies.dockerfile"

# Extract lychee version from dependencies.dockerfile
LYCHEE_VERSION=$(grep "FROM lycheeverse/lychee:" "$DEPENDENCIES_DOCKERFILE" | sed 's/.*FROM lycheeverse\/lychee:\([^ ]*\).*/\1/')

# Build the lychee command with optional GitHub token
CMD="lycheeverse/lychee:$LYCHEE_VERSION --verbose --config $(basename "$LYCHEE_CONFIG")"

# Add GitHub token if available
if [[ -n "$GITHUB_TOKEN" ]]; then
    CMD="$CMD --github-token $GITHUB_TOKEN"
fi

# Add the target directory
CMD="$CMD ."

# Determine if we should allocate a TTY
DOCKER_FLAGS="--rm --init"
if [[ -t 0 ]]; then
    DOCKER_FLAGS="$DOCKER_FLAGS -it"
else
    DOCKER_FLAGS="$DOCKER_FLAGS -i"
fi

# Run lychee with proper signal handling
# shellcheck disable=SC2086
exec docker run $DOCKER_FLAGS -v "$(dirname "$LYCHEE_CONFIG")":/data -w /data $CMD
