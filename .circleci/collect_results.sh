#!/usr/bin/env bash

# Save all important reports and artifacts into (project-root)/results
# This folder will be saved by circleci and available after test runs.

set -e
#Enable '**' support
shopt -s globstar

TEST_RESULTS_DIR=./results
mkdir -p $TEST_RESULTS_DIR >/dev/null 2>&1

echo "saving test results"
mkdir -p $TEST_RESULTS_DIR/results
find workspace/**/build/test-results -name \*.xml -exec cp {} $TEST_RESULTS_DIR \;
