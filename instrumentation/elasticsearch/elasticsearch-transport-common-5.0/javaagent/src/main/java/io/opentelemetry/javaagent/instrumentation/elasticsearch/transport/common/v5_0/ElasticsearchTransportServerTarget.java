/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import java.util.List;
import javax.annotation.Nullable;

public class ElasticsearchTransportServerTarget {

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
      return new ElasticsearchTransportServerTarget(endpoint.host, endpoint.port);
    }

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
      group.append(':').append(endpoint.port);
    }
    return new ElasticsearchTransportServerTarget(group.toString(), null);
  }

  private ElasticsearchTransportServerTarget(String address, @Nullable Integer port) {
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

  public static class Endpoint {

    @Nullable private final String host;
    private final int port;

    public Endpoint(@Nullable String host, int port) {
      this.host = host == null || host.isEmpty() ? null : host;
      this.port = port;
    }
  }
}
