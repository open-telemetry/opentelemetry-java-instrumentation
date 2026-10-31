#!/bin/bash -e

set -o pipefail

if command -v rg > /dev/null 2>&1; then
  source_dirs=$(MSYS2_ARG_CONV_EXCL="/" rg --files --path-separator "/" instrumentation -g '*.java' \
    | grep -E '/(library|javaagent)/src/main/java[0-9]*/' \
    | sed 's#/[^/]*$##' \
    | sort -u)
else
  source_dirs=$(find instrumentation \( -path "*/library/src/main/java*/*.java" -o -path "*/javaagent/src/main/java*/*.java" \) -print \
    | sed 's#/[^/]*$##' \
    | sort -u)
fi

if [[ -z "$source_dirs" ]]; then
  echo "ERROR: no Java source directories found"
  exit 1
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
      if [[ "$dir" == "instrumentation/thrift-0.13/library/src/main/java/org/apache/thrift" ]]; then
        continue
      fi
      if [[ "$dir" == "instrumentation/thrift-0.13/library/src/main/java/org/apache/thrift/server" ]]; then
        continue
      fi
    fi

    if [[ "$source_set" == "javaagent" ]]; then
      # advice and injected helper packages that must share a namespace with package-private
      # library types and members, including library instrumentation helpers
      case "$dir" in
        instrumentation/apache-dbcp-2.0/javaagent/src/main/java/org/apache/commons/dbcp2) continue ;;
        instrumentation/clickhouse/clickhouse-client-v1-0.5/javaagent/src/main/java/com/clickhouse/client | instrumentation/clickhouse/clickhouse-client-v1-0.5/javaagent/src/main/java/com/clickhouse/client/*) continue ;;
        instrumentation/finagle-http-23.11/javaagent/src/main/java/com/twitter/finagle | instrumentation/finagle-http-23.11/javaagent/src/main/java/com/twitter/finagle/*) continue ;;
        instrumentation/finagle-http-23.11/javaagent/src/main/java/io/netty/channel | instrumentation/finagle-http-23.11/javaagent/src/main/java/io/netty/channel/*) continue ;;
        instrumentation/hbase/hbase-client-common-1.0/javaagent/src/main/java/org/apache/hadoop/hbase/ipc) continue ;;
        instrumentation/jedis/jedis-3.0/javaagent/src/main/java/redis/clients/jedis) continue ;;
        instrumentation/reactor/reactor-netty/reactor-netty-1.0/javaagent/src/main/java/reactor/netty/http/client | instrumentation/reactor/reactor-netty/reactor-netty-1.0/javaagent/src/main/java/reactor/netty/http/client/*) continue ;;
        instrumentation/redisson/redisson-3.0/javaagent/src/main/java/org/redisson/config) continue ;;
        instrumentation/redisson/redisson-3.17/javaagent/src/main/java/org/redisson/config) continue ;;
        instrumentation/rocketmq/rocketmq-client-4.8/javaagent/src/main/java/io/opentelemetry/instrumentation/rocketmqclient/v4_8) continue ;;
        instrumentation/tomcat/tomcat-dbcp-8.0/javaagent/src/main/java/org/apache/tomcat/dbcp/dbcp2) continue ;;
        instrumentation/vertx/vertx-redis-client/vertx-redis-client-4.0/javaagent/src/main/java/io/vertx/redis/client/impl | instrumentation/vertx/vertx-redis-client/vertx-redis-client-4.0/javaagent/src/main/java/io/vertx/redis/client/impl/*) continue ;;
        instrumentation/vertx/vertx-sql-client/vertx-sql-client-common-4.0/javaagent/src/main/java/io/vertx/sqlclient/impl | instrumentation/vertx/vertx-sql-client/vertx-sql-client-common-4.0/javaagent/src/main/java/io/vertx/sqlclient/impl/*) continue ;;
      esac

      # self-instrumentation modules: these instrument OpenTelemetry's own code,
      # so the standard module-name <-> package-name convention (which treats the
      # first dash-separated token as the instrumented library's namespace) does
      # not apply; package layout is owned by these modules and reviewed by hand
      case "$dir" in
        instrumentation/opentelemetry-extension-annotations-1.0/javaagent/*) continue ;;
        instrumentation/opentelemetry-instrumentation-annotations-1.16/javaagent/*) continue ;;
        instrumentation/opentelemetry-instrumentation-api/opentelemetry-instrumentation-api-1.14/javaagent/*) continue ;;
      esac

      # javaagent modules with package layouts that differ from their module names
      case "$dir" in
        instrumentation/java-util-logging/javaagent/*) continue ;;
      esac
    fi

    # JAX-RS roles may follow a major.minor version. Unversioned common modules may
    # have a javax/jakarta variant, but no numeric version token in their prefix.
    # These rules allow module names; package layout is still checked below.
    if [[ ! "$module_name" =~ [0-9]$
      && ! "$module_name" =~ ^jaxrs-[0-9]+\.[0-9]+-(annotations|common)$
      && ! "$module_name" =~ ^[a-z][a-z0-9]*(-[a-z][a-z0-9]*)*-common(-(javax|jakarta))?$ ]]; then
      case "$source_set:$module_name" in
        # library:
        library:java-http-client) ;;
        library:java-http-server) ;;
        library:jdbc) ;;
        library:resources) ;;
        library:runtime-telemetry) ;;
        # javaagent:
        javaagent:executors) ;;
        javaagent:external-annotations) ;;
        javaagent:http-url-connection) ;;
        javaagent:internal-application-logger) ;;
        javaagent:internal-class-loader) ;;
        javaagent:internal-lambda) ;;
        javaagent:internal-reflection) ;;
        javaagent:internal-url-class-loader) ;;
        javaagent:java-http-client) ;;
        javaagent:java-http-server) ;;
        javaagent:jdbc) ;;
        javaagent:methods) ;;
        javaagent:rmi) ;;
        javaagent:runtime-telemetry) ;;
        javaagent:spring-boot-resources) ;;
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

    if [[ "$package_name" != "$expected_prefix/"* ]]; then
      echo "ERROR: $dir"
      exit 1
    fi

    # Module tokens may map to separate package segments, as in elasticsearch-rest and
    # elasticsearch.rest. Match only after consuming a full segment so v4_10 does not match v4_1.
    expected_package_name_normalized=${expected_package_name#"$expected_prefix/"}
    expected_package_name_normalized=${expected_package_name_normalized//\//}
    package_name=${package_name#"$expected_prefix/"}
    package_name_prefix=
    package_name_matches=false
    IFS='/' read -ra package_parts <<< "$package_name"
    for part in "${package_parts[@]}"; do
      package_name_prefix+="$part"
      if [[ "$package_name_prefix" == "$expected_package_name_normalized" ]]; then
        package_name_matches=true
        break
      fi
    done

    if [[ "$package_name_matches" != true ]]; then
      echo "ERROR: $dir"
      exit 1
    fi

  done <<< "$source_dirs"
}

check_source_set "library" "io/opentelemetry/instrumentation"
check_source_set "javaagent" "io/opentelemetry/javaagent/instrumentation"
