#!/bin/bash -e

check_source_set() {
  local source_set="$1"
  local expected_prefix="$2"

  for dir in $(find instrumentation -name "*.java" | grep "$source_set/src/main/java" | sed 's#/[^/]*$##' | sort -u); do

    module_name=$(echo "$dir" | sed "s#.*/\([^/]*\)/$source_set/src/main/java[0-9]*/.*#\1#")

    if [[ "$module_name" =~ ^java- ]]; then
      continue
    fi
    if [[ "$module_name" == "jmx-metrics" ]]; then
      continue
    fi
    if [[ "$module_name" == "runtime-telemetry" ]]; then
      continue
    fi
    if [[ "$module_name" == "servlet-common" ]]; then
      continue
    fi
    if [[ "$module_name" == "graphql-java-common-12.0" ]]; then
      continue
    fi

    # these are possibly problematic
    if [[ "$source_set" == "library" ]]; then
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
    fi

    if [[ "$source_set" == "javaagent" ]]; then
      # advice packages that must live under the instrumented library's own namespace
      case "$dir" in
        instrumentation/clickhouse/clickhouse-client-v1-0.5/javaagent/src/main/java/com/clickhouse/client*) continue ;;
        instrumentation/finagle-http-23.11/javaagent/src/main/java/com/twitter/finagle*) continue ;;
        instrumentation/finagle-http-23.11/javaagent/src/main/java/io/netty/channel*) continue ;;
        instrumentation/reactor/reactor-netty/reactor-netty-1.0/javaagent/src/main/java/reactor/netty/http/client*) continue ;;
        instrumentation/spring/spring-webmvc/spring-webmvc-3.1/javaagent/src/main/java/org/springframework/web/servlet/v3_1*) continue ;;
        instrumentation/spring/spring-webmvc/spring-webmvc-6.0/javaagent/src/main/java/org/springframework/web/servlet/v6_0*) continue ;;
        instrumentation/vertx/vertx-redis-client-4.0/javaagent/src/main/java/io/vertx/redis/client/impl*) continue ;;
        instrumentation/vertx/vertx-sql-client/vertx-sql-client-common-4.0/javaagent/src/main/java/io/vertx/sqlclient/impl*) continue ;;
      esac

      # historical javaagent modules that do not follow the module-name <-> package-name convention
      case "$dir" in
        instrumentation/akka/akka-actor-fork-join-2.5/javaagent/*) continue ;;
        instrumentation/akka/akka-http-10.0/javaagent/*) continue ;;
        instrumentation/aws-sdk/aws-sdk-1.11/javaagent/*) continue ;;
        instrumentation/aws-sdk/aws-sdk-2.2/javaagent/*) continue ;;
        instrumentation/camel-2.20/javaagent/*) continue ;;
        instrumentation/clickhouse/clickhouse-client-common/javaagent/*) continue ;;
        instrumentation/elasticsearch/elasticsearch-api-client-7.16/javaagent/*) continue ;;
        instrumentation/elasticsearch/elasticsearch-rest-common-5.0/javaagent/*) continue ;;
        instrumentation/elasticsearch/elasticsearch-transport-common/javaagent/*) continue ;;
        instrumentation/external-annotations/javaagent/*) continue ;;
        instrumentation/hibernate/hibernate-procedure-call-4.3/javaagent/*) continue ;;
        instrumentation/internal/internal-application-logger/javaagent/*) continue ;;
        instrumentation/internal/internal-eclipse-osgi-3.6/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-2.0/jaxrs-2.0-annotations/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-2.0/jaxrs-2.0-common/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-3.0/jaxrs-3.0-annotations/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-3.0/jaxrs-3.0-common/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-common/javaagent/*) continue ;;
        instrumentation/jaxws/jaxws-jws-api-1.1/javaagent/*) continue ;;
        instrumentation/jsf/jsf-mojarra-1.2/javaagent/*) continue ;;
        instrumentation/jsf/jsf-mojarra-3.0/javaagent/*) continue ;;
        instrumentation/jsf/jsf-myfaces-1.2/javaagent/*) continue ;;
        instrumentation/jsf/jsf-myfaces-3.0/javaagent/*) continue ;;
        instrumentation/kotlinx-coroutines/kotlinx-coroutines-1.0/javaagent/*) continue ;;
        instrumentation/kotlinx-coroutines/kotlinx-coroutines-flow-1.3/javaagent/*) continue ;;
        instrumentation/liberty/liberty-dispatcher-20.0/javaagent/*) continue ;;
        instrumentation/opensearch/opensearch-rest-common/javaagent/*) continue ;;
        instrumentation/opentelemetry-api/opentelemetry-api-1.0/javaagent/*) continue ;;
        instrumentation/opentelemetry-extension-annotations-1.0/javaagent/*) continue ;;
        instrumentation/opentelemetry-extension-kotlin-1.0/javaagent/*) continue ;;
        instrumentation/opentelemetry-instrumentation-annotations-1.16/javaagent/*) continue ;;
        instrumentation/opentelemetry-instrumentation-api/javaagent/*) continue ;;
        instrumentation/play/play-mvc/play-mvc-2.4/javaagent/*) continue ;;
        instrumentation/play/play-mvc/play-mvc-2.6/javaagent/*) continue ;;
        instrumentation/play/play-ws/play-ws-common/javaagent/*) continue ;;
        instrumentation/scala-fork-join-2.8/javaagent/*) continue ;;
        instrumentation/spark-2.3/javaagent/*) continue ;;
        instrumentation/spring/spring-boot-actuator-autoconfigure-2.0/javaagent/*) continue ;;
        instrumentation/spring/spring-boot-resources/javaagent/*) continue ;;
      esac
    fi

    # some common modules don't have any base version (might have a variant instead, ex: javax)
    if [[ ! "$module_name" =~ [0-9]$ ]]; then
      case "$source_set:$module_name" in
        # library:
        library:jdbc) ;;
        library:lettuce-common) ;;
        library:netty-common) ;;
        library:oshi) ;;
        library:resources) ;;
        library:servlet-common-javax) ;;
        # javaagent:
        javaagent:clickhouse-client-common) ;;
        javaagent:elasticsearch-transport-common) ;;
        javaagent:executors) ;;
        javaagent:external-annotations) ;;
        javaagent:http-url-connection) ;;
        javaagent:internal-application-logger) ;;
        javaagent:internal-class-loader) ;;
        javaagent:internal-lambda) ;;
        javaagent:internal-reflection) ;;
        javaagent:internal-url-class-loader) ;;
        javaagent:jaxrs-common) ;;
        javaagent:jaxws-common) ;;
        javaagent:jdbc) ;;
        javaagent:jetty-common) ;;
        javaagent:jsf-common-jakarta) ;;
        javaagent:jsf-common-javax) ;;
        javaagent:methods) ;;
        javaagent:opensearch-rest-common) ;;
        javaagent:opentelemetry-instrumentation-api) ;;
        javaagent:oshi) ;;
        javaagent:payara) ;;
        javaagent:play-ws-common) ;;
        javaagent:quarkus-resteasy-reactive) ;;
        javaagent:rmi) ;;
        javaagent:spring-boot-resources) ;;
        javaagent:spring-cloud-gateway-common) ;;
        javaagent:spring-webmvc-common) ;;
        javaagent:tomcat-common) ;;
        javaagent:tomcat-jdbc) ;;
        javaagent:xxl-job-common) ;;
        *)
          echo "module name doesn't have a base version: $dir"
          exit 1
          ;;
      esac
    fi

    # build expected package name by walking the module name's dash-separated tokens:
    # a version token (e.g. "3.0") becomes "/v3_0", any other token becomes "/<token>";
    # the literal token "java" is elided (e.g. graphql-java-20.0 -> graphql/v20_0).
    # this also handles multi-version modules like jaxrs-2.0-resteasy-3.1 -> jaxrs/v2_0/resteasy/v3_1.
    expected_package_name="$expected_prefix"
    IFS='-' read -ra module_parts <<< "$module_name"
    for part in "${module_parts[@]}"; do
      if [[ "$part" == "java" ]]; then
        continue
      fi
      if [[ "$part" =~ ^[0-9][0-9.]*$ ]]; then
        expected_package_name="$expected_package_name/v${part//./_}"
      else
        expected_package_name="$expected_package_name/$part"
      fi
    done

    package_name=$(echo "$dir" | sed 's#.*/src/main/java[0-9]*/##')

    # deal with differences like module name elasticsearch-rest and package name elasticsearch.rest
    expected_package_name_normalized=$(echo "$expected_package_name" | sed 's#/##g')
    package_name_normalized=$(echo "$package_name" | sed 's#/##g')

    if [[ "$package_name_normalized" != "$expected_package_name_normalized"* ]]; then
      echo "ERROR: $dir"
      exit 1
    fi

  done
}

check_source_set "library" "io/opentelemetry/instrumentation"
check_source_set "javaagent" "io/opentelemetry/javaagent/instrumentation"
