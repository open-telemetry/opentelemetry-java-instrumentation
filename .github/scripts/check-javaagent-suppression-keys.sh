#!/bin/bash -e

# shellcheck disable=SC2044
for file in $(find instrumentation -name "*Module.java"); do

  if ! grep -q "extends InstrumentationModule" "$file"; then
    continue
  fi

  if [[ "$file" != *"/javaagent/src/"* ]]; then
    continue
  fi

  # shellcheck disable=SC2001
  module_name=$(echo "$file" | sed 's#.*/\([^/]*\)/javaagent/src/.*#\1#')
  # shellcheck disable=SC2001
  simple_module_name=$(echo "$module_name" | sed 's/-[0-9.]*$//')

  if [[ "$simple_module_name" == *jaxrs* ]]; then
    # TODO these need some work still
    continue
  fi
  if [[ "$simple_module_name" == *jaxws* ]]; then
    # TODO these need some work still
    continue
  fi
  if [[ "$simple_module_name" == jdbc ]]; then
    # TODO split jdbc-datasource out into separate instrumentation?
    continue
  fi
  if [[ "$simple_module_name" == kafka-clients ]]; then
    # TODO split kafka client metrics out into separate instrumentation?
    continue
  fi
  if [[ "$simple_module_name" == quarkus-resteasy-reactive ]]; then
    # TODO module is missing a base version
    continue
  fi

  if [ "$module_name" == "$simple_module_name" ]; then
    expected="super\(\n? *\"$simple_module_name\""
  else
    expected="super\(\n? *\"$simple_module_name\",\n? *\"$module_name\""
  fi

  echo "$module_name"

  matches=$(perl -0 -ne "print if /$expected/" "$file" | wc -l)
  if [ "$matches" == 0 ]; then
    echo "Expected to find $expected in $file"
    exit 1
  fi

done
