/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

// Couchbase 2.x moved its connection string type and changed its seed representation three times.
// Reflection keeps this helper compatible across the full range. Prefer allHosts when available
// because 2.5.7 through 2.7.7 drop seeds that fail DNS resolution from hosts.
class CouchbaseConnectionStrings {

  private static final int COUCHBASE_DEFAULT_PORT = 11210;
  private static final int COUCHBASES_DEFAULT_PORT = 11207;
  private static final int HTTP_DEFAULT_PORT = 8091;
  private static final ClassValue<Method> ALL_HOSTS =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          return method(type, "allHosts");
        }
      };
  private static final ClassValue<Method> HOSTS =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          return method(type, "hosts");
        }
      };
  private static final ClassValue<Method> SCHEME =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          return method(type, "scheme");
        }
      };
  private static final ClassValue<Method> HOSTNAME =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          return method(type, "hostname");
        }
      };
  private static final ClassValue<Method> PORT =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          return method(type, "port");
        }
      };

  @Nullable
  static DbServerTarget target(@Nullable Object connectionString) {
    if (connectionString == null) {
      return null;
    }
    try {
      Class<?> type = connectionString.getClass();
      List<?> seeds = seeds(type, connectionString);
      if (seeds == null) {
        return null;
      }
      DbServerTargetBuilder builder =
          DbServerTarget.builder(defaultPort(scheme(type, connectionString)));
      for (Object seed : seeds) {
        addSeed(builder, seed);
      }
      return builder.build();
    } catch (ReflectiveOperationException ignored) {
      // An unsupported connection-string shape leaves the stable server target unset. Legacy
      // semantic conventions report the contacted node later.
      return null;
    } catch (RuntimeException ignored) {
      // Treat malformed driver-provided values as unparseable so instrumentation never disrupts
      // cluster construction.
      return null;
    }
  }

  private static int defaultPort(@Nullable String scheme) {
    if ("couchbase".equalsIgnoreCase(scheme)) {
      return COUCHBASE_DEFAULT_PORT;
    }
    if ("couchbases".equalsIgnoreCase(scheme)) {
      return COUCHBASES_DEFAULT_PORT;
    }
    if ("http".equalsIgnoreCase(scheme)) {
      return HTTP_DEFAULT_PORT;
    }
    return -1;
  }

  @Nullable
  private static List<?> seeds(Class<?> type, Object connectionString)
      throws ReflectiveOperationException {
    Method allHosts = ALL_HOSTS.get(type);
    Method hosts = allHosts != null ? allHosts : HOSTS.get(type);
    if (hosts == null) {
      return null;
    }
    Object value = hosts.invoke(connectionString);
    return value instanceof List ? (List<?>) value : null;
  }

  @Nullable
  private static String scheme(Class<?> type, Object connectionString)
      throws ReflectiveOperationException {
    Method scheme = SCHEME.get(type);
    if (scheme == null) {
      return null;
    }
    Object value = scheme.invoke(connectionString);
    return value == null ? null : value.toString();
  }

  private static void addSeed(DbServerTargetBuilder builder, @Nullable Object seed)
      throws ReflectiveOperationException {
    if (seed == null) {
      builder.addEndpoint(null, -1);
      return;
    }
    if (seed instanceof InetSocketAddress) {
      InetSocketAddress address = (InetSocketAddress) seed;
      // getHostString never triggers a reverse lookup, unlike getHostName
      builder.addEndpoint(cleanHost(address.getHostString()), configuredPort(address.getPort()));
      return;
    }
    Class<?> type = seed.getClass();
    Method hostname = HOSTNAME.get(type);
    Method port = PORT.get(type);
    if (hostname == null || port == null) {
      builder.addEndpoint(null, -1);
      return;
    }
    Object host = hostname.invoke(seed);
    Object value = port.invoke(seed);
    builder.addEndpoint(
        cleanHost(host == null ? null : host.toString()),
        configuredPort(value instanceof Number ? ((Number) value).intValue() : 0));
  }

  private static int configuredPort(int port) {
    return port > 0 ? port : -1;
  }

  @Nullable
  private static String cleanHost(@Nullable String host) {
    if (host == null) {
      return null;
    }
    // Older parsers retain credentials and connection-string suffixes in the seed host.
    String cleaned = truncateAt(truncateAt(truncateAt(host.trim(), '/'), '?'), '#');
    int credentialsEnd = cleaned.lastIndexOf('@');
    if (credentialsEnd >= 0) {
      cleaned = cleaned.substring(credentialsEnd + 1);
    }
    return cleaned;
  }

  private static String truncateAt(String host, char separator) {
    int index = host.indexOf(separator);
    return index < 0 ? host : host.substring(0, index);
  }

  @Nullable
  private static Method method(Class<?> type, String name) {
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private CouchbaseConnectionStrings() {}
}
