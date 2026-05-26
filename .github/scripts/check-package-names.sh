#!/bin/bash -e

if command -v rg > /dev/null 2>&1; then
  case "$(uname -s)" in
    CYGWIN* | MINGW* | MSYS*) path_separator="//" ;;
    *) path_separator="/" ;;
  esac

  source_dirs=$(rg --files --path-separator "$path_separator" instrumentation -g '*.java' \
    | grep -E '/(library|javaagent)/src/main/java[0-9]*/' \
    | sed 's#/[^/]*$##' \
    | sort -u)
else
  source_dirs=$(find instrumentation \( -path "*/library/src/main/java*/*.java" -o -path "*/javaagent/src/main/java*/*.java" \) -print \
    | sed 's#/[^/]*$##' \
    | sort -u)
fi

check_source_set() {
  local source_set="$1"
  local expected_prefix="$2"

  while IFS= read -r dir; do
    if [[ "$dir" != *"/$source_set/src/main/java"* ]]; then
      continue
    fi

    module_path=${dir%%/$source_set/src/main/java*}
    module_name=${module_path##*/}

    if [[ "$module_name" == "jmx-metrics" ]]; then
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
      if [[ "$dir" == "instrumentation/elasticsearch/elasticsearch-rest-7.0/library/src/main/java/org/elasticsearch/client" ]]; then
        continue
      fi
      if [[ "$dir" == "instrumentation/servlet/servlet-common/library/src/main/java/io/opentelemetry/instrumentation/servlet/internal" ]]; then
        continue
      fi
      if [[ "$dir" == instrumentation/java-http-client/library/src/main/java/io/opentelemetry/instrumentation/javahttpclient* ]]; then
        continue
      fi
      if [[ "$dir" == instrumentation/java-http-server/library/src/main/java/io/opentelemetry/instrumentation/javahttpserver* ]]; then
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
        instrumentation/spring/spring-boot-resources/javaagent/src/main/java/io/opentelemetry/instrumentation/spring/resources) continue ;;
        instrumentation/spring/spring-webmvc/spring-webmvc-3.1/javaagent/src/main/java/org/springframework/web/servlet/v3_1*) continue ;;
        instrumentation/spring/spring-webmvc/spring-webmvc-6.0/javaagent/src/main/java/org/springframework/web/servlet/v6_0*) continue ;;
        instrumentation/vertx/vertx-redis-client-4.0/javaagent/src/main/java/io/vertx/redis/client/impl*) continue ;;
        instrumentation/vertx/vertx-sql-client/vertx-sql-client-common-4.0/javaagent/src/main/java/io/vertx/sqlclient/impl*) continue ;;
      esac

      # historical javaagent modules that do not follow the module-name <-> package-name convention
      case "$dir" in
        instrumentation/akka/akka-actor-fork-join-2.5/javaagent/*) continue ;;
        instrumentation/aws-sdk/aws-sdk-1.11/javaagent/src/main/java/io/opentelemetry/instrumentation/awssdk/v1_11) continue ;;
        instrumentation/aws-sdk/aws-sdk-2.2/javaagent/src/main/java/io/opentelemetry/instrumentation/awssdk/v2_2/internal) continue ;;
        instrumentation/java-http-client/javaagent/*) continue ;;
        instrumentation/java-http-server/javaagent/*) continue ;;
        instrumentation/java-util-logging/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-2.0/jaxrs-2.0-annotations/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-2.0/jaxrs-2.0-common/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-3.0/jaxrs-3.0-annotations/javaagent/*) continue ;;
        instrumentation/jaxrs/jaxrs-3.0/jaxrs-3.0-common/javaagent/*) continue ;;
        instrumentation/opentelemetry-api/opentelemetry-api-1.0/javaagent/*) continue ;;
        instrumentation/opentelemetry-extension-annotations-1.0/javaagent/*) continue ;;
        instrumentation/opentelemetry-instrumentation-annotations-1.16/javaagent/*) continue ;;
        instrumentation/opentelemetry-instrumentation-api/javaagent/*) continue ;;
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
        library:runtime-telemetry) ;;
        library:servlet-common) ;;
        library:servlet-common-javax) ;;
        # javaagent:
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
        javaagent:jdbc) ;;
        javaagent:jsf-common-jakarta) ;;
        javaagent:jsf-common-javax) ;;
        javaagent:methods) ;;
        javaagent:opentelemetry-instrumentation-api) ;;
        javaagent:oshi) ;;
        javaagent:rmi) ;;
        javaagent:runtime-telemetry) ;;
        javaagent:servlet-common) ;;
        javaagent:spring-boot-resources) ;;
        javaagent:spring-cloud-gateway-common) ;;
        *)
          echo "module name doesn't have a base version: $dir"
          exit 1
          ;;
      esac
    fi

    # build expected package name by walking the module name's dash-separated tokens:
    # a version token (e.g. "3.0") becomes "/v3_0", any other token becomes "/<token>";
    # the literal token "java" is elided except when it is the leading token in java-* modules
    # where it identifies a JDK instrumentation (e.g. graphql-java-20.0 -> graphql/v20_0,
    # but java-http-client -> java/http/client).
    # this also handles multi-version modules like jaxrs-2.0-resteasy-3.1 -> jaxrs/v2_0/resteasy/v3_1.
    expected_package_name="$expected_prefix"
    IFS='-' read -ra module_parts <<< "$module_name"
    for i in "${!module_parts[@]}"; do
      part=${module_parts[$i]}
      if [[ "$part" == "java" && "$i" != 0 ]]; then
        continue
      fi
      if [[ "$part" =~ ^[0-9][0-9.]*$ ]]; then
        expected_package_name="$expected_package_name/v${part//./_}"
      else
        expected_package_name="$expected_package_name/$part"
      fi
    done

    if [[ "$dir" =~ /src/main/java[0-9]*/(.*)$ ]]; then
      package_name=${BASH_REMATCH[1]}
    else
      echo "ERROR: $dir"
      exit 1
    fi

    # deal with differences like module name elasticsearch-rest and package name elasticsearch.rest
    expected_package_name_normalized=${expected_package_name//\//}
    package_name_normalized=${package_name//\//}

    if [[ "$package_name_normalized" != "$expected_package_name_normalized"* ]]; then
      echo "ERROR: $dir"
      exit 1
    fi

  done <<< "$source_dirs"
}

check_source_set "library" "io/opentelemetry/instrumentation"
check_source_set "javaagent" "io/opentelemetry/javaagent/instrumentation"
