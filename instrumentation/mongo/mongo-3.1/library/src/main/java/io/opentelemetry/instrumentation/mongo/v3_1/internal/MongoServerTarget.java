/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.ServerAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MongoServerTarget {

  private static final int DEFAULT_PORT = 27017;
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
    String host = sanitizeSrvHost(srvHost);
    return host.isEmpty() ? null : new MongoServerTarget(SRV_SCHEME + host, null);
  }

  @Nullable
  public static MongoServerTarget seeds(@Nullable List<ServerAddress> seeds) {
    if (seeds == null || seeds.isEmpty()) {
      return null;
    }

    List<String> hosts = new ArrayList<>(seeds.size());
    List<Integer> ports = new ArrayList<>(seeds.size());
    boolean hasSharedPort = true;
    Integer sharedPort = null;
    for (ServerAddress seed : seeds) {
      String host = host(seed);
      if (host == null) {
        return null;
      }
      Integer port = isUnixSocket(host) ? null : seed.getPort();
      hosts.add(host);
      ports.add(port);
      if (hosts.size() == 1) {
        sharedPort = port;
      } else if (!Objects.equals(sharedPort, port)) {
        hasSharedPort = false;
      }
    }

    List<String> addresses = new ArrayList<>(seeds.size());
    for (int i = 0; i < hosts.size(); i++) {
      addresses.add(hasSharedPort ? hosts.get(i) : endpoint(hosts.get(i), ports.get(i)));
    }
    Collections.sort(addresses, String::compareTo);

    StringBuilder address = new StringBuilder();
    for (String value : addresses) {
      if (address.length() > 0) {
        address.append(',');
      }
      address.append(value);
    }
    Integer port =
        hasSharedPort && sharedPort != null && sharedPort != DEFAULT_PORT ? sharedPort : null;
    return new MongoServerTarget(address.toString(), port);
  }

  private MongoServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  private static String endpoint(String host, @Nullable Integer port) {
    if (port == null) {
      return host;
    }
    if (host.indexOf(':') >= 0) {
      return "[" + host + "]:" + port;
    }
    return host + ":" + port;
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

  private static String sanitizeSrvHost(String value) {
    int schemeSeparator = value.indexOf("://");
    String host = schemeSeparator < 0 ? value : value.substring(schemeSeparator + 3);
    int end = host.length();
    for (char separator : new char[] {'/', '?', '#'}) {
      int index = host.indexOf(separator);
      if (index >= 0 && index < end) {
        end = index;
      }
    }
    host = host.substring(0, end);
    int credentialsSeparator = host.lastIndexOf('@');
    return credentialsSeparator < 0 ? host : host.substring(credentialsSeparator + 1);
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
