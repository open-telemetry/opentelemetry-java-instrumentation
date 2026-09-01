#!/bin/bash -e

# GitHub Actions runners have only provide 14 GB of disk space which we have been exceeding regularly
# https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources

df -h
sudo rm -rf /usr/local/lib/android
sudo rm -rf /usr/share/dotnet
sudo rm -rf /usr/local/julia*
sudo rm -rf /usr/share/swift
sudo rm -rf /usr/local/.ghcup
# GitHub-hosted runners can include about 1.9 GB of unused GitHub and Dependabot images. Remove
# them before Testcontainers starts pulling test images. Each job gets a fresh runner, so this
# does not clean up Docker data from an earlier workflow run.
# Docker may not be running yet on Windows. Skip pruning in that case, but fail the job if a
# running daemon cannot be pruned.
if docker info >/dev/null 2>&1; then
  docker system prune -af --volumes
else
  echo "Docker daemon is unavailable; skipping Docker prune." >&2
fi
df -h
