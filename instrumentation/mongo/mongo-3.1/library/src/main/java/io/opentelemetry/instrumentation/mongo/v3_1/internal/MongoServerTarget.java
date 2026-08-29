/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.ServerAddress;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MongoServerTarget {

  private static final String SRV_SCHEME = "mongodb+srv://";

  // the driver reports its default port for socket paths, but the port is not part of the target
  private static final String UNIX_SOCKET_SUFFIX = ".sock";

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static MongoServerTarget srvHost(@Nullable String srvHost) {
    if (srvHost == null || srvHost.isEmpty()) {
      return null;
    }
    return new MongoServerTarget(SRV_SCHEME + srvHost, null);
  }

  @Nullable
  public static MongoServerTarget seeds(@Nullable List<ServerAddress> seeds) {
    if (seeds == null || seeds.isEmpty()) {
      return null;
    }
    if (seeds.size() == 1) {
      return single(seeds.get(0));
    }

    StringBuilder address = new StringBuilder();
    for (ServerAddress seed : seeds) {
      String host = host(seed);
      if (host == null) {
        return null;
      }
      if (address.length() > 0) {
        address.append(',');
      }
      if (isUnixSocket(host)) {
        address.append(host);
      } else {
        if (host.indexOf(':') >= 0) {
          address.append('[').append(host).append(']');
        } else {
          address.append(host);
        }
        address.append(':').append(seed.getPort());
      }
    }
    return new MongoServerTarget(address.toString(), null);
  }

  private MongoServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static MongoServerTarget single(@Nullable ServerAddress seed) {
    if (seed == null) {
      return null;
    }
    String host = host(seed);
    if (host == null) {
      return null;
    }
    return new MongoServerTarget(host, isUnixSocket(host) ? null : seed.getPort());
  }

  @Nullable
  private static String host(@Nullable ServerAddress seed) {
    if (seed == null || seed.getHost() == null || seed.getHost().isEmpty()) {
      return null;
    }
    String host = stripBrackets(seed.getHost());
    return host.isEmpty() ? null : host;
  }

  // server.address uses the host without URI brackets around IPv6 literals
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

  @Nullable
  public Integer getPort() {
    return port;
  }
}
