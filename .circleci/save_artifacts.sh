#!/bin/bash

# Save all important reports and artifacts into (project-root)/build
# This folder will be saved by circleci and available after test runs.

ARTIFACT_DIR=./build/
TEST_RESULTS_DIR=./build/test-results

mkdir -p $ARTIFACT_DIR >/dev/null 2>&1
mkdir -p $TEST_RESULTS_DIR >/dev/null 2>&1

function save_reports () {
    project_to_save=$1
    if [ -d $project_to_save/build/reports ]; then
        report_path=$ARTIFACT_DIR/$project_to_save/build
        mkdir -p $report_path
        cp -r $project_to_save/build/reports $report_path/
    fi
    if [ -d $project_to_save/build/test-results ]; then
        find "$project_to_save/build/test-results" -name \*.xml -exec cp {} $TEST_RESULTS_DIR \;
    fi
}

function save_libs () {
    project_to_save=$1
    if [ -d $project_to_save/build/libs ]; then
        libs_path=$ARTIFACT_DIR/$project_to_save/build
        mkdir -p $libs_path
        cp -r $project_to_save/build/libs $libs_path/
    fi
}

save_reports dd-java-agent
save_reports dd-java-agent/tooling
# Save reports for all instrumentation projects
for integration_path in dd-java-agent/instrumentation/*; do
    save_reports $integration_path
done
save_reports dd-java-agent-ittests
save_reports dd-trace

save_libs dd-java-agent
save_libs dd-trace
