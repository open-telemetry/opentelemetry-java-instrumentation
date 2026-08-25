/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import java.util.List;
import javax.annotation.Nullable;

/**
 * The target a transport client was configured with, rendered once from the addresses it was given.
 *
 * <p>A client configured with a single address keeps that address and its port. A client configured
 * with several carries all of them in the address, in the client's own {@code host:port,host:port}
 * syntax, and has no port of its own.
 */
public final class ElasticsearchTransportServerTarget {

  private final String address;
  @Nullable private final Integer port;

  private ElasticsearchTransportServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  /** The target of {@code endpoints}, or {@code null} when there is no usable endpoint. */
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
      return new ElasticsearchTransportServerTarget(
          endpoint.host, endpoint.port >= 0 ? endpoint.port : null);
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
      // a literal IPv6 address is bracketed so that the port stays unambiguous
      if (endpoint.host.indexOf(':') >= 0 && !endpoint.host.startsWith("[")) {
        group.append('[').append(endpoint.host).append(']');
      } else {
        group.append(endpoint.host);
      }
      if (endpoint.port >= 0) {
        group.append(':').append(endpoint.port);
      }
    }
    return new ElasticsearchTransportServerTarget(group.toString(), null);
  }

  public String getAddress() {
    return address;
  }

  /**
   * The port of a single configured address, or {@code null} when the target names several of them.
   */
  @Nullable
  public Integer getPort() {
    return port;
  }

  /** A single configured address. */
  public static final class Endpoint {

    @Nullable private final String host;
    private final int port;

    public Endpoint(@Nullable String host, int port) {
      this.host = host == null || host.isEmpty() ? null : host;
      this.port = port;
    }
  }
}
