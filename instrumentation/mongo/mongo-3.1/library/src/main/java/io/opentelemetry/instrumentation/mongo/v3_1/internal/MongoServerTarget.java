/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.ServerAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
  private static final int MAX_ENDPOINTS = 5;
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
    return host.isEmpty() || !isSafeHost(host, false)
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

  public static boolean isSrvConnectionString(@Nullable String connectionString) {
    return connectionString != null
        && connectionString.regionMatches(true, 0, SRV_SCHEME, 0, SRV_SCHEME.length());
  }

  @Nullable
  public static MongoServerTarget seeds(@Nullable List<ServerAddress> seeds) {
    if (seeds == null || seeds.isEmpty()) {
      return null;
    }

    List<String> hosts = new ArrayList<>(seeds.size());
    List<Integer> ports = new ArrayList<>(seeds.size());
    boolean hasNonDefaultPort = false;
    boolean hasUnixSocket = false;
    for (ServerAddress seed : seeds) {
      String host = host(seed);
      if (host == null) {
        return null;
      }
      boolean unixSocket = isUnixSocket(seed, host);
      hasUnixSocket |= unixSocket;
      Integer port = unixSocket ? null : seed.getPort();
      if (!containsEndpoint(hosts, ports, host, port)) {
        hosts.add(host);
        ports.add(port);
        hasNonDefaultPort |= port != null && port != DEFAULT_PORT;
      }
    }
    if (seeds.size() > 1 && hasUnixSocket) {
      return null;
    }

    boolean multipleEndpoints = hosts.size() > 1;
    boolean includePorts = multipleEndpoints && hasNonDefaultPort;
    List<String> addresses = new ArrayList<>(seeds.size());
    for (int i = 0; i < hosts.size(); i++) {
      addresses.add(includePorts ? endpoint(hosts.get(i), ports.get(i)) : hosts.get(i));
    }
    Collections.sort(addresses, String::compareTo);

    StringBuilder address = new StringBuilder();
    int endpointCount = Math.min(addresses.size(), MAX_ENDPOINTS);
    for (int i = 0; i < endpointCount; i++) {
      if (address.length() != 0) {
        address.append(',');
      }
      address.append(addresses.get(i));
    }
    Integer port = !multipleEndpoints && hasNonDefaultPort ? ports.get(0) : null;
    return new MongoServerTarget(address.toString(), port);
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
    return host.isEmpty() || !isSafeHost(host, isUnixSocket(seed, host)) ? null : host;
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

  private static boolean isSafeHost(String host, boolean unixSocket) {
    if (unixSocket) {
      return host.indexOf('@') < 0
          && host.indexOf('%') < 0
          && host.indexOf('=') < 0
          && host.indexOf('?') < 0
          && host.indexOf('#') < 0;
    }
    if (host.indexOf('@') >= 0
        || host.indexOf('/') >= 0
        || host.indexOf('\\') >= 0
        || host.indexOf('?') >= 0
        || host.indexOf('#') >= 0) {
      return false;
    }
    if (host.indexOf(':') >= 0) {
      return isIpv6Literal(host);
    }
    if (host.indexOf('%') >= 0) {
      return false;
    }
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '-') {
        return false;
      }
    }
    return true;
  }

  private static boolean isIpv6Literal(String host) {
    int zoneSeparator = host.indexOf('%');
    String address = zoneSeparator < 0 ? host : host.substring(0, zoneSeparator);
    if (zoneSeparator >= 0) {
      String zone = host.substring(zoneSeparator + 1);
      if (zone.isEmpty() || zone.indexOf('%') >= 0) {
        return false;
      }
      if (startsWithEncodedDelimiter(zone)) {
        return false;
      }
      for (int i = 0; i < zone.length(); i++) {
        char c = zone.charAt(i);
        if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '-') {
          return false;
        }
      }
    }

    try {
      InetAddress.getByName(address);
      return true;
    } catch (UnknownHostException e) {
      return false;
    }
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

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }
}
