/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class OpenSearchServerTarget {

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static OpenSearchServerTarget of(@Nullable List<Endpoint> endpoints) {
    if (endpoints == null || endpoints.isEmpty()) {
      return null;
    }
    if (endpoints.size() == 1) {
      Endpoint endpoint = endpoints.get(0);
      if (endpoint.host == null) {
        return null;
      }
      int port = normalizePort(endpoint);
      return new OpenSearchServerTarget(endpoint.host, port >= 0 ? port : null);
    }
    return renderGroup(endpoints);
  }

  private OpenSearchServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static OpenSearchServerTarget renderGroup(List<Endpoint> endpoints) {
    List<String> addresses = new ArrayList<>(endpoints.size());
    List<Integer> effectivePorts = new ArrayList<>(endpoints.size());
    int sharedPort = effectivePort(endpoints.get(0));
    boolean portsMatch = true;
    boolean allPortsAreDefault = true;
    for (Endpoint endpoint : endpoints) {
      if (endpoint.host == null) {
        return null;
      }
      int port = effectivePort(endpoint);
      if (port != sharedPort) {
        portsMatch = false;
      }
      if (port != defaultPort(endpoint)) {
        allPortsAreDefault = false;
      }
      addresses.add(endpoint.host);
      effectivePorts.add(port);
    }
    List<String> renderedEndpoints = new ArrayList<>(endpoints.size());
    for (int i = 0; i < endpoints.size(); i++) {
      renderedEndpoints.add(
          renderEndpoint(addresses.get(i), portsMatch ? -1 : effectivePorts.get(i)));
    }
    renderedEndpoints.sort(String::compareTo);
    return new OpenSearchServerTarget(
        String.join(",", renderedEndpoints),
        portsMatch && sharedPort >= 0 && !allPortsAreDefault ? sharedPort : null);
  }

  private static String renderEndpoint(String host, int port) {
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

  private static int normalizePort(Endpoint endpoint) {
    int port = effectivePort(endpoint);
    return port == defaultPort(endpoint) ? -1 : port;
  }

  private static int effectivePort(Endpoint endpoint) {
    return endpoint.port >= 0 ? endpoint.port : defaultPort(endpoint);
  }

  private static int defaultPort(Endpoint endpoint) {
    if (endpoint.scheme.equalsIgnoreCase("http")) {
      return 80;
    }
    if (endpoint.scheme.equalsIgnoreCase("https")) {
      return 443;
    }
    return -1;
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
    private final String scheme;

    public Endpoint(@Nullable String host, int port, String scheme) {
      this.host = sanitizeHost(host);
      this.port = port;
      this.scheme = scheme;
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
  }
}
