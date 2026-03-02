#!/bin/bash -e

for dir in $(find instrumentation -name "*.java" | grep library/src/main/java | sed 's#/[^/]*$##' | sort -u); do

  module_name=$(echo "$dir" | sed 's#.*/\([^/]*\)/library/src/main/java[0-9]*/.*#\1#')

  if [[ "$module_name" =~ ^java- ]]; then
    continue
  fi
  if [[ "$module_name" == "jmx-metrics" ]]; then
    continue
  fi
  if [[ "$module_name" == "runtime-telemetry" ]]; then
    continue
  fi
  if [[ "$module_name" == "runtime-telemetry-java8" ]]; then
    continue
  fi
  if [[ "$module_name" == "runtime-telemetry-java17" ]]; then
    continue
  fi
  if [[ "$module_name" == "servlet-common" ]]; then
    continue
  fi
  if [[ "$module_name" == "graphql-java-common-12.0" ]]; then
    continue
  fi
  if [[ "$module_name" == "servlet-javax-common" ]]; then
    continue
  fi

  # these are possibly problematic
  if [[ "$dir" == "instrumentation/grpc-1.6/library/src/main/java/io/grpc/override" ]]; then
    continue
  fi
  if [[ "$dir" == "instrumentation/lettuce/lettuce-5.1/library/src/main/java/io/lettuce/core/protocol" ]]; then
    continue
  fi
  if [[ "$dir" == "instrumentation/nats/nats-2.17/library/src/main/java/io/nats/client/impl" ]]; then
    continue
  fi
  if [[ "$dir" == "instrumentation/rxjava/rxjava-1.0/library/src/main/java/rx" ]]; then
    continue
  fi

  # some common modules don't have any base version
  # - jdbc
  # - lettuce-common
  # - netty-common
  # - oshi
  # - resources
  if [[ ! "$module_name" =~ [0-9]$ && "$module_name" != "jdbc" && "$module_name" != "lettuce-common" && "$module_name" != "netty-common" && "$module_name" != "oshi" && "$module_name" != "resources" ]]; then
    echo "module name doesn't have a base version: $dir"
    exit 1
  fi

  # convention: if module ends with -java (followed by version), remove -java from the package name
  simple_module_name=$(echo "$module_name" | sed 's/-[0-9.]*$//' | sed 's/-java$//' | sed 's/-//g')
  base_version=$(echo "$module_name" | sed 's/.*-\([0-9.]*\)$/\1/' | sed 's/\./_/g')

  if [[ "$module_name" =~ [0-9]$ ]]; then
    expected_package_name="io/opentelemetry/instrumentation/$simple_module_name/v$base_version"
  else
    expected_package_name="io/opentelemetry/instrumentation/$simple_module_name"
  fi

  package_name=$(echo "$dir" | sed 's#.*/src/main/java[0-9]*/##')

  # deal with differences like module name elasticsearch-rest and package name elasticsearch.rest
  expected_package_name_normalized=$(echo "$expected_package_name" | sed 's#/##g')
  package_name_normalized=$(echo "$package_name" | sed 's#/##g')

  if [[ "$package_name_normalized" != "$expected_package_name_normalized"* ]]; then
    echo "ERROR: $dir"
    exit 1
  fi

done
