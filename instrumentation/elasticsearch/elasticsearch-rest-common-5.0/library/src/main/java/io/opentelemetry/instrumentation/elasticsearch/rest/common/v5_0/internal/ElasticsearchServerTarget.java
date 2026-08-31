/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ElasticsearchServerTarget {

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static ElasticsearchServerTarget of(@Nullable List<HttpHost> hosts) {
    if (hosts == null || hosts.isEmpty()) {
      return null;
    }
    if (hosts.size() == 1) {
      HttpHost httpHost = hosts.get(0);
      String host = sanitizeHost(httpHost.getHostName());
      if (host == null) {
        return null;
      }
      int port = normalizePort(httpHost);
      return new ElasticsearchServerTarget(host, port >= 0 ? port : null);
    }
    return renderGroup(hosts);
  }

  private ElasticsearchServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static ElasticsearchServerTarget renderGroup(List<HttpHost> hosts) {
    List<String> addresses = new ArrayList<>(hosts.size());
    int sharedPort = normalizePort(hosts.get(0));
    boolean portsMatch = true;
    for (HttpHost httpHost : hosts) {
      String host = sanitizeHost(httpHost.getHostName());
      if (host == null) {
        return null;
      }
      int port = normalizePort(httpHost);
      if (port != sharedPort) {
        portsMatch = false;
      }
      addresses.add(host);
    }
    List<String> endpoints = new ArrayList<>(hosts.size());
    for (int i = 0; i < hosts.size(); i++) {
      endpoints.add(
          renderHostAndPort(addresses.get(i), portsMatch ? -1 : normalizePort(hosts.get(i))));
    }
    endpoints.sort(String::compareTo);
    return new ElasticsearchServerTarget(
        String.join(",", endpoints), portsMatch && sharedPort >= 0 ? sharedPort : null);
  }

  private static int normalizePort(HttpHost httpHost) {
    int port = httpHost.getPort();
    if ((port == 80 && httpHost.getSchemeName().equalsIgnoreCase("http"))
        || (port == 443 && httpHost.getSchemeName().equalsIgnoreCase("https"))) {
      return -1;
    }
    return port;
  }

  private static String renderHostAndPort(String host, int port) {
    StringBuilder endpoint = new StringBuilder();
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      endpoint.append('[').append(host).append(']');
    } else {
      endpoint.append(host);
    }
    if (port >= 0) {
      endpoint.append(':').append(port);
    }
    return endpoint.toString();
  }

  @Nullable
  private static String sanitizeHost(@Nullable String hostName) {
    if (hostName == null) {
      return null;
    }
    String host = hostName;
    int authorityEnd = host.length();
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        authorityEnd = i;
        break;
      }
    }
    int credentialsEnd = host.lastIndexOf('@');
    if (credentialsEnd >= authorityEnd) {
      return null;
    }
    host = host.substring(credentialsEnd + 1, authorityEnd);
    if (host.length() >= 2 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
      host = host.substring(1, host.length() - 1);
    }
    return host.isEmpty() ? null : host;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }
}
