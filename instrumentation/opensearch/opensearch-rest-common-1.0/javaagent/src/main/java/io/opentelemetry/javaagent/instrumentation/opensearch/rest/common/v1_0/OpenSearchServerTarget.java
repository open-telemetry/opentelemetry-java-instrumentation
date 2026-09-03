/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
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

    DbServerTargetBuilder builder = DbServerTarget.builder(-1);
    for (Endpoint endpoint : endpoints) {
      builder.addEndpoint(endpoint.host, endpoint.port, defaultPort(endpoint));
    }
    DbServerTarget target = builder.build();
    return target == null
        ? null
        : new OpenSearchServerTarget(target.getAddress(), target.getPort());
  }

  private OpenSearchServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
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
      this.host = host;
      this.port = port;
      this.scheme = scheme;
    }
  }
}
