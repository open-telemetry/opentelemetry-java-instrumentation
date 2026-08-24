/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.ServerAddress;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The target a MongoDB client was configured with, rendered once from the cluster settings it was
 * built from.
 *
 * <p>A client configured with a single seed keeps that host and its port. A client configured with
 * several seeds carries all of them in the address, in the driver's own {@code
 * mongodb://host:port,host:port} connection string syntax, and has no port of its own. A client
 * configured with an SRV host is named by that host, a single name that carries no port.
 *
 * <p>Cluster settings hold the hosts the driver has already parsed out of a connection string, so
 * the rendered text never contains credentials, a database, a path, query parameters, options or a
 * fragment. The required replica set name is deliberately left out: it names a set of servers
 * rather than an address, and two clients pointed at different deployments can require the same
 * one.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MongoServerTarget {

  // the scheme of a connection string that lists its hosts, see
  // https://www.mongodb.com/docs/manual/reference/connection-string/
  private static final String SEED_LIST_SCHEME = "mongodb://";

  // a unix domain socket is configured as a path rather than a host, and the driver reports the
  // default port next to it; that port is not part of the address, so it is left out
  private static final String UNIX_SOCKET_SUFFIX = ".sock";

  private final String address;
  @Nullable private final Integer port;

  /**
   * The target of a client configured with an SRV host, or {@code null} when it was configured with
   * seeds instead.
   *
   * <p>An SRV host takes precedence over the seeds, because a client that resolves one is given a
   * placeholder seed list naming a host it never talks to.
   */
  @Nullable
  public static MongoServerTarget srvHost(@Nullable String srvHost) {
    if (srvHost == null || srvHost.isEmpty()) {
      return null;
    }
    return new MongoServerTarget(srvHost, null);
  }

  /**
   * The target of a client configured with the given seeds, or {@code null} when it names none.
   *
   * <p>The seeds are rendered eagerly, so that a client keeps reporting the target it was built
   * with even after the driver discovers the deployment behind it and stops talking to some of
   * them.
   */
  @Nullable
  public static MongoServerTarget seeds(@Nullable List<ServerAddress> seeds) {
    if (seeds == null || seeds.isEmpty()) {
      return null;
    }
    if (seeds.size() == 1) {
      return single(seeds.get(0));
    }
    StringBuilder group = new StringBuilder(SEED_LIST_SCHEME);
    for (ServerAddress seed : seeds) {
      if (seed == null || seed.getHost() == null || seed.getHost().isEmpty()) {
        return null;
      }
      if (group.length() > SEED_LIST_SCHEME.length()) {
        group.append(',');
      }
      appendHostAndPort(group, seed);
    }
    return new MongoServerTarget(group.toString(), null);
  }

  private MongoServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static MongoServerTarget single(@Nullable ServerAddress seed) {
    if (seed == null || seed.getHost() == null || seed.getHost().isEmpty()) {
      return null;
    }
    String host = stripBrackets(seed.getHost());
    if (host.isEmpty()) {
      return null;
    }
    return new MongoServerTarget(host, isUnixSocket(host) ? null : seed.getPort());
  }

  private static void appendHostAndPort(StringBuilder group, ServerAddress seed) {
    String host = seed.getHost();
    if (isUnixSocket(host)) {
      group.append(host);
      return;
    }
    // a literal ipv6 address is bracketed so that the port stays unambiguous; driver 3.3 to 3.7
    // hands one over bracketed already
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      group.append('[').append(host).append(']');
    } else {
      group.append(host);
    }
    group.append(':').append(seed.getPort());
  }

  // a single host is reported on its own, where brackets would only get in the way of matching it
  // against a configured peer
  private static String stripBrackets(String host) {
    if (host.startsWith("[") && host.endsWith("]")) {
      return host.substring(1, host.length() - 1);
    }
    return host;
  }

  private static boolean isUnixSocket(String host) {
    return host.endsWith(UNIX_SOCKET_SUFFIX);
  }

  public String getAddress() {
    return address;
  }

  /** The port of a single configured seed, or {@code null} when the target names several. */
  @Nullable
  public Integer getPort() {
    return port;
  }
}
