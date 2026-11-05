/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.util.List;
import javax.annotation.Nullable;

public class ElasticsearchTransportServerTarget {

  private static final int DEFAULT_PORT = 9300;

  @Nullable
  public static DbServerTarget of(@Nullable List<Endpoint> endpoints) {
    if (endpoints == null || endpoints.isEmpty()) {
      return null;
    }

    DbServerTargetBuilder builder = DbServerTarget.builder(DEFAULT_PORT).setSorted(true);
    for (Endpoint endpoint : endpoints) {
      builder.addEndpoint(endpoint.host, endpoint.port);
    }
    return builder.build();
  }

  public static class Endpoint {

    @Nullable private final String host;
    private final int port;

    public Endpoint(@Nullable String host, int port) {
      this.host = host;
      this.port = port;
    }
  }

  private ElasticsearchTransportServerTarget() {}
}
