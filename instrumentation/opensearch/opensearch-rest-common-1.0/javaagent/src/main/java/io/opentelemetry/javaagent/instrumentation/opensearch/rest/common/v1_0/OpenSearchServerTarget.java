/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

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
    StringBuilder group = new StringBuilder();
    for (int i = 0; i < endpoints.size(); i++) {
      Endpoint endpoint = endpoints.get(i);
      if (endpoint.host == null) {
        return null;
      }
      if (i > 0) {
        group.append(',');
      }
      if (endpoint.host.indexOf(':') >= 0 && !endpoint.host.startsWith("[")) {
        group.append('[').append(endpoint.host).append(']');
      } else {
        group.append(endpoint.host);
      }
      if (endpoint.port >= 0) {
        group.append(':').append(endpoint.port);
      }
    }
    return group.toString();
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
