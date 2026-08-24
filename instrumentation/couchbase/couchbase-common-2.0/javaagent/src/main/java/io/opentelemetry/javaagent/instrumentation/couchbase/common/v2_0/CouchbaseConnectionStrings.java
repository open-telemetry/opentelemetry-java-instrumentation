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

/**
 * Reads the target a Couchbase 2.x client was configured with out of the connection string its
 * cluster was built from.
 *
 * <p>The connection string is read reflectively because the 2.x line moved it and changed its shape
 * three times: driver 2.0 to 2.3 parse into {@code com.couchbase.client.java.ConnectionString},
 * driver 2.4 to 2.6 into {@code com.couchbase.client.core.utils.ConnectionString} with seeds
 * modelled as {@link InetSocketAddress}, and driver 2.7 keeps that class but models seeds as its
 * own {@code UnresolvedSocket}. Only one of those classes is on the class path of any given
 * application, so naming them would keep the instrumentation off every other driver version.
 *
 * <p>Driver 2.4 to 2.6 resolve the seeds while parsing and drop the ones that do not resolve, so
 * the unresolved list is read when the driver keeps one.
 */
public final class CouchbaseConnectionStrings {

  /**
   * The target {@code connectionString} names, or {@code null} when it cannot be read.
   *
   * <p>A host that resolves through DNS SRV is the lone seed of its connection string, so it is
   * reported as itself rather than as the seeds the driver looked up. That holds for every driver
   * in the 2.x line, none of which record the looked up seeds on the connection string.
   */
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
      CouchbaseServerTarget.Builder target =
          CouchbaseServerTarget.builder(scheme(type, connectionString));
      for (Object seed : seeds) {
        addSeed(target, seed);
      }
      return target.build();
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // an unknown connection string shape leaves the client without a configured target
      return null;
    }
  }

  @Nullable
  private static String scheme(Class<?> type, Object connectionString)
      throws ReflectiveOperationException {
    Method scheme = method(type, "scheme");
    if (scheme == null) {
      return null;
    }
    Object value = scheme.invoke(connectionString);
    return value == null ? null : value.toString();
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
