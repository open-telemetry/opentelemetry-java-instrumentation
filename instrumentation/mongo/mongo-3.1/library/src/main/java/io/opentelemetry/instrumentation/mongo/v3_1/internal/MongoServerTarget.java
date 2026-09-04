/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.ServerAddress;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MongoServerTarget {

  private static final int DEFAULT_PORT = 27017;
  private static final String SRV_SCHEME = "mongodb+srv://";
  private static final String UNIX_SERVER_ADDRESS_CLASS = "com.mongodb.UnixServerAddress";

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
    DbServerTarget target =
        DbServerTarget.builder(DEFAULT_PORT).addEndpoint(host, DEFAULT_PORT).build();
    return target == null || !target.getAddress().equals(host)
        ? null
        : new MongoServerTarget(SRV_SCHEME + host, null);
  }

  @Nullable
  public static MongoServerTarget srvConnectionString(@Nullable String connectionString) {
    if (!isSrvConnectionString(connectionString)) {
      return null;
    }
    return srvHost(connectionString);
  }

  @Nullable
  public static MongoServerTarget seeds(@Nullable List<ServerAddress> seeds) {
    if (seeds == null || seeds.isEmpty()) {
      return null;
    }

    List<String> hosts = new ArrayList<>(seeds.size());
    List<Integer> ports = new ArrayList<>(seeds.size());
    DbServerTarget unixSocketTarget = null;
    for (ServerAddress seed : seeds) {
      String host = host(seed);
      if (host == null) {
        return null;
      }
      boolean unixSocket = isUnixSocket(seed, host);
      if (unixSocket) {
        unixSocketTarget = DbServerTarget.unixSocket(host);
        if (unixSocketTarget == null) {
          return null;
        }
      } else if (hasUnsafeEncodedIpv6Zone(host)) {
        return null;
      }
      Integer port = unixSocket ? null : seed.getPort();
      if (!containsEndpoint(hosts, ports, host, port)) {
        hosts.add(host);
        ports.add(port);
      }
    }
    if (unixSocketTarget != null) {
      if (seeds.size() > 1) {
        return null;
      }
      return from(unixSocketTarget);
    }

    DbServerTargetBuilder targetBuilder = DbServerTarget.builder(DEFAULT_PORT).setSorted(true);
    for (int i = 0; i < hosts.size(); i++) {
      targetBuilder.addEndpoint(hosts.get(i), ports.get(i));
    }
    return from(targetBuilder.build());
  }

  private MongoServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public static boolean isSrvConnectionString(@Nullable String connectionString) {
    return connectionString != null
        && connectionString.regionMatches(true, 0, SRV_SCHEME, 0, SRV_SCHEME.length());
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  @Nullable
  private static MongoServerTarget from(@Nullable DbServerTarget target) {
    if (target == null) {
      return null;
    }
    return new MongoServerTarget(target.getAddress(), target.getPort());
  }

  private static boolean containsEndpoint(
      List<String> hosts, List<Integer> ports, String host, @Nullable Integer port) {
    for (int i = 0; i < hosts.size(); i++) {
      if (hosts.get(i).equals(host) && Objects.equals(ports.get(i), port)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static String host(@Nullable ServerAddress seed) {
    if (seed == null || seed.getHost() == null || seed.getHost().isEmpty()) {
      return null;
    }
    return stripBrackets(seed.getHost());
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

  private static boolean hasUnsafeEncodedIpv6Zone(String host) {
    int zoneSeparator = host.indexOf('%');
    return zoneSeparator >= 0 && startsWithEncodedDelimiter(host.substring(zoneSeparator + 1));
  }

  private static boolean startsWithEncodedDelimiter(String value) {
    if (value.length() < 2) {
      return false;
    }
    int high = Character.digit(value.charAt(0), 16);
    int low = Character.digit(value.charAt(1), 16);
    if (high < 0 || low < 0) {
      return false;
    }
    char decoded = (char) ((high << 4) + low);
    return decoded == ':'
        || decoded == '@'
        || decoded == '/'
        || decoded == '?'
        || decoded == '#'
        || decoded == '\\'
        || decoded == '%'
        || decoded == '=';
  }

  private static boolean isUnixSocket(ServerAddress seed, String host) {
    return seed.getClass().getName().equals(UNIX_SERVER_ADDRESS_CLASS)
        || (host.startsWith("/") && host.endsWith(UNIX_SOCKET_SUFFIX));
  }
}
