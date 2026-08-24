/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;

/**
 * The target a rest client was configured with, rendered once from the hosts the client was built
 * with.
 *
 * <p>A client configured with a single host keeps that host and its port. A client configured with
 * several hosts carries all of them in the address, in the client's own {@code
 * scheme://host:port,host:port} syntax, and has no port of its own.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ElasticsearchServerTarget {

  private final String address;
  @Nullable private final Integer port;

  private ElasticsearchServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  /** The target of {@code hosts}, or {@code null} when there is no usable host. */
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

  @Nullable
  private static String renderGroup(List<HttpHost> hosts) {
    String sharedScheme = hosts.get(0).getSchemeName();
    for (HttpHost host : hosts) {
      if (sharedScheme == null || !sharedScheme.equals(host.getSchemeName())) {
        sharedScheme = null;
        break;
      }
    }

    StringBuilder group = new StringBuilder();
    if (sharedScheme != null) {
      group.append(sharedScheme).append("://");
    }
    for (int i = 0; i < hosts.size(); i++) {
      HttpHost httpHost = hosts.get(i);
      String host = sanitizeHost(httpHost.getHostName());
      if (host == null) {
        return null;
      }
      if (i > 0) {
        group.append(',');
      }
      if (sharedScheme == null && httpHost.getSchemeName() != null) {
        group.append(httpHost.getSchemeName()).append("://");
      }
      appendHostAndPort(group, host, httpHost.getPort());
    }
    return group.toString();
  }

  private static void appendHostAndPort(StringBuilder group, String host, int port) {
    // a literal IPv6 address is bracketed so that the port stays unambiguous
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      group.append('[').append(host).append(']');
    } else {
      group.append(host);
    }
    if (port >= 0) {
      group.append(':').append(port);
    }
  }

  /**
   * The host name without credentials, path, query or fragment, or {@code null} when nothing is
   * left of it.
   */
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
    return host.isEmpty() ? null : host;
  }

  public String getAddress() {
    return address;
  }

  /** The port of a single configured host, or {@code null} when the target names several hosts. */
  @Nullable
  public Integer getPort() {
    return port;
  }
}
