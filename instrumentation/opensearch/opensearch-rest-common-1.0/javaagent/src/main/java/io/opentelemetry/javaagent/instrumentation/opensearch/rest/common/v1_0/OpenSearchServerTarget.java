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
      return new OpenSearchServerTarget(endpoint.host, endpoint.port >= 0 ? endpoint.port : null);
    }
    String group = renderGroup(endpoints);
    return group == null ? null : new OpenSearchServerTarget(group, null);
  }

  private OpenSearchServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static String renderGroup(List<Endpoint> endpoints) {
    List<String> renderedEndpoints = new ArrayList<>(endpoints.size());
    for (Endpoint endpoint : endpoints) {
      if (endpoint.host == null) {
        return null;
      }
      renderedEndpoints.add(renderEndpoint(endpoint.host, endpoint.port));
    }
    renderedEndpoints.sort(String::compareTo);
    return String.join(",", renderedEndpoints);
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
  }
}
