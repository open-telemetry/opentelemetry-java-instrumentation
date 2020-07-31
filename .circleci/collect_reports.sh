#!/usr/bin/env bash

# Save all important reports into (project-root)/reports
# This folder will be saved by circleci and available after test runs.

set -e
#Enable '**' support
shopt -s globstar

REPORTS_DIR=./reports
mkdir -p $REPORTS_DIR >/dev/null 2>&1

cp /tmp/hs_err_pid*.log $REPORTS_DIR || true

function save_reports () {
    project_to_save=$1
    echo "saving reports for $project_to_save"

    report_path=$REPORTS_DIR/$project_to_save
    mkdir -p $report_path
    cp -r workspace/$project_to_save/build/reports/* $report_path/
}

shopt -s globstar

for report_path in workspace/**/build/reports; do
    report_path=${report_path//workspace\//}
    report_path=${report_path//\/build\/reports/}
    save_reports $report_path
done
