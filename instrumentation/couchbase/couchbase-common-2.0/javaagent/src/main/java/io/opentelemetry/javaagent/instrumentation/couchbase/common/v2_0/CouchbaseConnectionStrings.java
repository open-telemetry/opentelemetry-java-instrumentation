/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

// Couchbase 2.x moved its connection string type and changed its seed representation three times.
// Reflection keeps this helper compatible across the full range. Prefer allHosts when available
// because 2.4-2.6 drop seeds that fail DNS resolution from hosts.
public class CouchbaseConnectionStrings {

  @Nullable
  public static CouchbaseServerTarget target(@Nullable Object connectionString) {
    if (connectionString == null) {
      return null;
    }
    try {
      Class<?> type = connectionString.getClass();
      List<?> seeds = seeds(type, connectionString);
      if (seeds == null) {
        return null;
      }
      CouchbaseServerTarget.Builder target = CouchbaseServerTarget.builder();
      for (Object seed : seeds) {
        addSeed(target, seed);
      }
      return target.build();
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // Stable semantic conventions omit the server target when parsing fails. Legacy semantic
      // conventions report the contacted node later.
      return null;
    }
  }

  @Nullable
  private static List<?> seeds(Class<?> type, Object connectionString)
      throws ReflectiveOperationException {
    Method allHosts = method(type, "allHosts");
    Method hosts = allHosts != null ? allHosts : method(type, "hosts");
    if (hosts == null) {
      return null;
    }
    Object value = hosts.invoke(connectionString);
    return value instanceof List ? (List<?>) value : null;
  }

  private static void addSeed(CouchbaseServerTarget.Builder target, @Nullable Object seed)
      throws ReflectiveOperationException {
    if (seed == null) {
      target.addSeed(null, 0);
      return;
    }
    if (seed instanceof InetSocketAddress) {
      InetSocketAddress address = (InetSocketAddress) seed;
      // getHostString never triggers a reverse lookup, unlike getHostName
      target.addSeed(address.getHostString(), address.getPort());
      return;
    }
    Class<?> type = seed.getClass();
    Method hostname = method(type, "hostname");
    Method port = method(type, "port");
    if (hostname == null || port == null) {
      target.addSeed(null, 0);
      return;
    }
    Object host = hostname.invoke(seed);
    Object value = port.invoke(seed);
    target.addSeed(
        host == null ? null : host.toString(),
        value instanceof Number ? ((Number) value).intValue() : 0);
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
