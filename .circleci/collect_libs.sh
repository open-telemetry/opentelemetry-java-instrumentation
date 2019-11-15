#!/usr/bin/env bash

# Save all important libs into (project-root)/libs
# This folder will be saved by circleci and available after test runs.

set -x
set -e

LIBS_DIR=./libs/
mkdir -p $LIBS_DIR >/dev/null 2>&1

for lib_path in workspace/*/build/libs; do
    echo "saving libs in $lib_path"
    cp $lib_path/*.jar $LIBS_DIR/
done
