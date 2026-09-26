#!/bin/bash -e

for file in $(find instrumentation -name "*Module.java"); do

  if ! grep -q "extends InstrumentationModule" "$file"; then
    continue
  fi

  if [[ "$file" != *"/javaagent/src/"* ]]; then
    continue
  fi

  module_name=$(echo "$file" | sed 's#.*/\([^/]*\)/javaagent/src/.*#\1#')
  if [[ "$file" == instrumentation/ratpack/ratpack-1.4/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/ratpack/v1_7/RatpackInstrumentationModule.java ]]; then
    # The 1.7 module shares a Gradle project with 1.4 but has its own enablement alias.
    module_name="ratpack-1.7"
  fi
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
  if [[ "$simple_module_name" == spring-cloud-gateway-webmvc ]]; then
    # webmvc variant uses spring-cloud-gateway as base name
    simple_module_name="spring-cloud-gateway"
  fi

  if [ "$module_name" == "$simple_module_name" ]; then
    expected="super\(\n? *\"$simple_module_name\""
  else
    expected="super\(\n? *\"$simple_module_name\",\n? *\"$module_name\""
  fi

  echo "$module_name"

  matches=$(perl -0 -ne "print if /$expected/" "$file" | wc -l)
  if [ "$matches" == 0 ]; then
    if grep -q "expandDeprecatedNames" "$file" \
      && { grep -q "\"$simple_module_name|deprecated:" "$file" \
        || perl -0ne "exit !/super\(\s*\"$simple_module_name\"/" "$file"; } \
      && { [ "$module_name" == "$simple_module_name" ] || grep -q "\"$module_name|deprecated:" "$file"; }
    then
      continue
    fi
    echo "Expected to find $expected in $file"
    exit 1
  fi

done
