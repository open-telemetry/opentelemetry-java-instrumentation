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
      String host = sanitizeHost(hosts.get(0).getHostName());
      if (host == null) {
        return null;
      }
      int port = hosts.get(0).getPort();
      return new ElasticsearchServerTarget(host, port >= 0 ? port : null);
    }
    String group = renderGroup(hosts);
    return group == null ? null : new ElasticsearchServerTarget(group, null);
  }

  private ElasticsearchServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static String renderGroup(List<HttpHost> hosts) {
    List<String> endpoints = new ArrayList<>(hosts.size());
    for (HttpHost httpHost : hosts) {
      String host = sanitizeHost(httpHost.getHostName());
      if (host == null) {
        return null;
      }
      endpoints.add(renderHostAndPort(host, httpHost.getPort()));
    }
    endpoints.sort(String::compareTo);
    return String.join(",", endpoints);
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
