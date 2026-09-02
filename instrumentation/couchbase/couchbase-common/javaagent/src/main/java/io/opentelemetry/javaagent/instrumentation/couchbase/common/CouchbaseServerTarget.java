/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class CouchbaseServerTarget {

  private static final int COUCHBASE_DEFAULT_PORT = 11210;
  private static final int COUCHBASES_DEFAULT_PORT = 11207;
  private static final int HTTP_DEFAULT_PORT = 8091;
  private static final int MAX_ENDPOINTS = 5;

  private final String address;
  @Nullable private final Integer port;

  public static Builder builder() {
    return builderWithDefaultPort(0);
  }

  public static Builder builder(@Nullable String scheme) {
    int defaultPort = 0;
    if ("couchbase".equalsIgnoreCase(scheme)) {
      defaultPort = COUCHBASE_DEFAULT_PORT;
    } else if ("couchbases".equalsIgnoreCase(scheme)) {
      defaultPort = COUCHBASES_DEFAULT_PORT;
    } else if ("http".equalsIgnoreCase(scheme)) {
      defaultPort = HTTP_DEFAULT_PORT;
    }
    return builderWithDefaultPort(defaultPort);
  }

  public static Builder builderWithDefaultPort(int defaultPort) {
    return new Builder(defaultPort > 0 ? defaultPort : 0);
  }

  private CouchbaseServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  public static class Builder {

    private final int defaultPort;
    private final List<String> hosts = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    private boolean complete = true;

    private Builder(int defaultPort) {
      this.defaultPort = defaultPort;
    }

    public void addSeed(@Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null) {
        // A partial seed list describes a target the client was never configured with.
        complete = false;
        return;
      }
      hosts.add(cleaned);
      ports.add(port > 0 ? port : 0);
    }

    @Nullable
    public CouchbaseServerTarget build() {
      if (!complete || hosts.isEmpty()) {
        return null;
      }
      boolean multipleEndpoints = hosts.size() > 1;
      boolean inlinePorts = false;
      if (multipleEndpoints) {
        for (int port : ports) {
          if (effectivePort(port) != defaultPort) {
            inlinePorts = true;
            break;
          }
        }
      }
      List<String> endpoints = new ArrayList<>(Math.min(hosts.size(), MAX_ENDPOINTS));
      for (int i = 0; i < hosts.size(); i++) {
        StringBuilder endpoint = new StringBuilder();
        appendSeed(
            endpoint,
            hosts.get(i),
            inlinePorts ? effectivePort(ports.get(i)) : 0,
            multipleEndpoints);
        endpoints.add(endpoint.toString());
      }
      endpoints.sort(String::compareTo);
      StringBuilder group = new StringBuilder();
      for (int i = 0; i < endpoints.size() && i < MAX_ENDPOINTS; i++) {
        if (group.length() > 0) {
          group.append(',');
        }
        group.append(endpoints.get(i));
      }
      Integer port = null;
      if (!multipleEndpoints) {
        int effectivePort = effectivePort(ports.get(0));
        if (effectivePort > 0 && effectivePort != defaultPort) {
          port = effectivePort;
        }
      }
      return new CouchbaseServerTarget(group.toString(), port);
    }

    private int effectivePort(int port) {
      return port > 0 ? port : defaultPort;
    }

    private static void appendSeed(
        StringBuilder group, String host, int port, boolean groupedEndpoint) {
      // a literal ipv6 address is bracketed so that the port stays unambiguous
      if (host.indexOf(':') >= 0 && (port > 0 || groupedEndpoint)) {
        group.append('[').append(host).append(']');
      } else {
        group.append(host);
      }
      if (port > 0) {
        group.append(':').append(port);
      }
    }

    @Nullable
    private static String clean(@Nullable String host) {
      if (host == null) {
        return null;
      }
      // Older parsers retain credentials and connection-string suffixes in the seed host.
      String cleaned = truncateAt(truncateAt(truncateAt(host.trim(), '/'), '?'), '#');
      int credentialsEnd = cleaned.lastIndexOf('@');
      if (credentialsEnd >= 0) {
        cleaned = cleaned.substring(credentialsEnd + 1);
      }
      if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
        cleaned = cleaned.substring(1, cleaned.length() - 1);
      }
      return cleaned.isEmpty() ? null : cleaned;
    }

    private static String truncateAt(String host, char separator) {
      int index = host.indexOf(separator);
      return index < 0 ? host : host.substring(0, index);
    }
  }
}
