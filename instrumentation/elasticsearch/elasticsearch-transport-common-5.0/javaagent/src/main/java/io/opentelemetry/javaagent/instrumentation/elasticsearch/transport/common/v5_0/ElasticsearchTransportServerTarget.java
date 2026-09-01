/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class ElasticsearchTransportServerTarget {

  private static final int DEFAULT_PORT = 9300;
  private static final int MAX_ADDRESS_LENGTH = 255;

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
      String address = joinEndpointsWithinLimit(singletonList(endpoint.host));
      if (address == null) {
        return null;
      }
      int port = normalizePort(endpoint.port);
      return new ElasticsearchTransportServerTarget(address, port >= 0 ? port : null);
    }

    List<String> addresses = new ArrayList<>(endpoints.size());
    int sharedPort = endpoints.get(0).port;
    boolean portsMatch = true;
    for (Endpoint endpoint : endpoints) {
      String host = endpoint.host;
      if (host == null) {
        return null;
      }
      if (endpoint.port != sharedPort) {
        portsMatch = false;
      }
      addresses.add(host);
    }

    List<String> renderedEndpoints = new ArrayList<>(endpoints.size());
    for (int i = 0; i < endpoints.size(); i++) {
      renderedEndpoints.add(
          renderEndpoint(addresses.get(i), portsMatch ? -1 : endpoints.get(i).port));
    }
    renderedEndpoints.sort(String::compareTo);

    String address = joinEndpointsWithinLimit(renderedEndpoints);
    if (address == null) {
      return null;
    }
    int port = normalizePort(sharedPort);
    return new ElasticsearchTransportServerTarget(address, portsMatch && port >= 0 ? port : null);
  }

  @Nullable
  private static String joinEndpointsWithinLimit(List<String> endpoints) {
    StringBuilder address = new StringBuilder();
    for (String endpoint : endpoints) {
      int separatorLength = address.length() == 0 ? 0 : 1;
      int available = MAX_ADDRESS_LENGTH - address.length() - separatorLength;
      if (endpoint.length() > available) {
        break;
      }
      if (separatorLength != 0) {
        address.append(',');
      }
      address.append(endpoint);
    }
    return address.length() == 0 ? null : address.toString();
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
