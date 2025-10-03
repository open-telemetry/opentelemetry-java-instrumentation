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
    # TODO: JAX-RS modules use "jaxrs" as the main instrumentation name instead of the module name,
    # providing multiple alternative names (jaxrs, jaxrs-X.Y, framework-name) for flexibility.
    # This allows users to disable all JAX-RS instrumentation with a single key.
    # Future work: evaluate if this pattern should be standardized or changed.
    continue
  fi
  if [[ "$simple_module_name" == *jaxws* ]]; then
    # TODO: JAX-WS modules use "jaxws" as the main instrumentation name instead of the module name,
    # similar to JAX-RS. Future work: align with the standard pattern or document the exception.
    continue
  fi
  if [[ "$simple_module_name" == jdbc ]]; then
    # TODO: The jdbc directory contains two separate InstrumentationModules:
    # - JdbcInstrumentationModule with super("jdbc") 
    # - DataSourceInstrumentationModule with super("jdbc-datasource")
    # Consider splitting jdbc-datasource into a separate instrumentation directory to follow
    # the standard pattern where each directory contains only one InstrumentationModule.
    continue
  fi
  if [[ "$simple_module_name" == kafka-clients ]]; then
    # TODO: The kafka-clients-X.Y directory contains two InstrumentationModules:
    # - KafkaClientsInstrumentationModule with super("kafka-clients", "kafka-clients-X.Y", "kafka")
    # - KafkaMetricsInstrumentationModule with super("kafka-clients-metrics", ...)
    # Consider splitting kafka-clients-metrics into a separate instrumentation directory.
    continue
  fi
  if [[ "$simple_module_name" == quarkus-resteasy-reactive ]]; then
    # TODO: quarkus-resteasy-reactive should be versioned in the directory name
    # (e.g., quarkus-resteasy-reactive-3.0) to follow the standard pattern where
    # the directory name matches the versioned instrumentation name.
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
