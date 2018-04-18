#!/bin/bash

# Save all important reports and artifacts into (project-root)/build
# This folder will be saved by circleci and available after test runs.

set -e

REPORTS_DIR=./reports
mkdir -p $REPORTS_DIR >/dev/null 2>&1

ARTIFACT_DIR=./artifacts/
mkdir -p $ARTIFACT_DIR >/dev/null 2>&1

function save_reports () {
    project_to_save=$1
    echo "saving reports for $project_to_save"

    report_path=$REPORTS_DIR/$project_to_save/reports
    mkdir -p $report_path
    cp -r workspace/$project_to_save/build/reports/* $report_path/
}

function save_libs () {
    project_to_save=$1
    echo "saving libs for $project_to_save"

    libs_path=$ARTIFACT_DIR/$project_to_save/libs
    mkdir -p $libs_path
    cp -r workspace/$project_to_save/build/libs/* $libs_path/
}


function save_results () {
    echo "saving test results"
    mkdir -p $REPORTS_DIR/results
    find workspace/**/build/test-results -name \*.xml -exec cp {} $REPORTS_DIR/results \;
}

save_reports dd-java-agent/agent-tooling
save_reports dd-java-agent/testing
# Save reports for all instrumentation projects
for integration_path in dd-java-agent/instrumentation/*; do
    if [ -d "workspace/$integration_path/build/reports" ]; then
        save_reports $integration_path
    fi
done
save_reports dd-java-agent-ittests
save_reports dd-trace-ot

save_libs dd-java-agent
save_libs dd-trace-api
save_libs dd-trace-ot

save_results
