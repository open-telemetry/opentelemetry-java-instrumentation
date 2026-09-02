/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class ElasticsearchTransportServerTarget {

  private static final int DEFAULT_PORT = 9300;
  private static final int MAX_ENDPOINTS = 5;

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static ElasticsearchTransportServerTarget of(@Nullable List<Endpoint> endpoints) {
    if (endpoints == null || endpoints.isEmpty()) {
      return null;
    }
    if (endpoints.size() == 1) {
      Endpoint endpoint = endpoints.get(0);
      if (endpoint.host == null) {
        return null;
      }
      int port = normalizePort(endpoint.port);
      return new ElasticsearchTransportServerTarget(endpoint.host, port >= 0 ? port : null);
    }

    List<String> addresses = new ArrayList<>(endpoints.size());
    boolean includePorts = false;
    for (Endpoint endpoint : endpoints) {
      String host = endpoint.host;
      if (host == null) {
        return null;
      }
      if (endpoint.port != DEFAULT_PORT) {
        includePorts = true;
      }
      addresses.add(host);
    }

    List<String> renderedEndpoints = new ArrayList<>(endpoints.size());
    for (int i = 0; i < endpoints.size(); i++) {
      renderedEndpoints.add(
          renderEndpoint(addresses.get(i), includePorts ? endpoints.get(i).port : -1));
    }
    renderedEndpoints.sort(String::compareTo);

    int endpointCount = Math.min(renderedEndpoints.size(), MAX_ENDPOINTS);
    String address = String.join(",", renderedEndpoints.subList(0, endpointCount));
    return new ElasticsearchTransportServerTarget(address, null);
  }

  private ElasticsearchTransportServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  private static String renderEndpoint(String host, int port) {
    String renderedHost = host;
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      renderedHost = "[" + host + "]";
    }
    return port >= 0 ? renderedHost + ":" + port : renderedHost;
  }

  private static int normalizePort(int port) {
    return port == DEFAULT_PORT ? -1 : port;
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
    if (host.indexOf(',') >= 0) {
      return null;
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

  public static class Endpoint {

    @Nullable private final String host;
    private final int port;

    public Endpoint(@Nullable String host, int port) {
      this.host = sanitizeHost(host);
      this.port = port;
    }
  }
}
