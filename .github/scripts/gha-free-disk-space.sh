#!/bin/bash -e

# GitHub Actions runners have only provide 14 GB of disk space which we have been exceeding regularly
# https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources

df -h
sudo rm -rf /usr/local/lib/android
sudo rm -rf /usr/share/dotnet
sudo rm -rf /usr/local/julia*
sudo rm -rf /usr/share/swift
sudo rm -rf /usr/local/.ghcup
# test suites pull many testcontainers images (elasticsearch, kafka connect, etc.) over the
# course of a single job; prune whatever shipped with the runner image first to make room.
# The reclaimable amount is runner-image dependent; GitHub-hosted jobs start on a fresh runner, so
# this cannot reclaim Docker data from a previous workflow run.
# On Windows, the Docker service can be stopped at this point; skip pruning in that case so the
# workflow can start the service in the following step. Once the daemon is available, keep prune
# failures fatal so that real cleanup errors are not hidden.
if docker info >/dev/null 2>&1; then
  docker system prune -af --volumes
else
  echo "Docker daemon is unavailable; skipping Docker prune." >&2
fi
df -h
