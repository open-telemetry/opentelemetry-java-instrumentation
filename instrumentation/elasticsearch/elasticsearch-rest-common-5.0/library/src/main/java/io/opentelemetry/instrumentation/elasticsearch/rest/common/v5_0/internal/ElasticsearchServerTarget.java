/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;

@SuppressWarnings("OtelInternalJavadoc")
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
    StringBuilder group = new StringBuilder();
    for (int i = 0; i < hosts.size(); i++) {
      HttpHost httpHost = hosts.get(i);
      String host = sanitizeHost(httpHost.getHostName());
      if (host == null) {
        return null;
      }
      if (i > 0) {
        group.append(',');
      }
      appendHostAndPort(group, host, httpHost.getPort());
    }
    return group.toString();
  }

  private static void appendHostAndPort(StringBuilder group, String host, int port) {
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      group.append('[').append(host).append(']');
    } else {
      group.append(host);
    }
    if (port >= 0) {
      group.append(':').append(port);
    }
  }

  @Nullable
  private static String sanitizeHost(@Nullable String hostName) {
    if (hostName == null) {
      return null;
    }
    String host = hostName;
    int credentialsEnd = host.lastIndexOf('@');
    if (credentialsEnd >= 0) {
      host = host.substring(credentialsEnd + 1);
    }
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        host = host.substring(0, i);
        break;
      }
    }
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
