#!/usr/bin/env bash

# Exit on error:
set -e

# Debug mode:
set -x

function print_usage() {
  echo "Usage: $0 search_directory build_resources_output_directory"
}

search_directory=$1
if [ ! -d "$search_directory" ]; then
  echo "Must specify a valid search_directory"
  print_usage
  exit 1
fi

build_resources_output_directory=$2
if [ ! -d "$build_resources_output_directory" ]; then
  echo "Must specify a valid build_resources_output_directory"
  print_usage
  exit 1
fi
# Add the full package path.
build_resources_output_directory="$build_resources_output_directory/datadog/trace/agent/jmxfetch"

# Find all the metrics.yaml files containing "jmx_metrics:"
metrics_files=$(grep --include=metrics.yaml -rwl $search_directory -e 'jmx_metrics:')

if [ -z "$metrics_files" ]; then
  echo "No metrics.yaml files with jmx_metrics blocks found."
  print_usage
  exit 1
fi

# reset file and ensure directories exists
mkdir -p $build_resources_output_directory/metricconfigs
> $build_resources_output_directory/metricconfigs.txt

for input_file in $metrics_files ; do
  # generate new name based on integration folder name which should look like this:
  # integrations-core/<integration_name>/datadog_checks/<integration_name>/data/metrics.yaml
  output_file=$(echo "$input_file" | awk -F/ '{print $2}')

  # save file name in metricconfigs.txt
  echo "$output_file.yaml" >> $build_resources_output_directory/metricconfigs.txt

  # copy to output location
  output_file="$build_resources_output_directory/metricconfigs/$output_file.yaml"
  cp $input_file $output_file
done

