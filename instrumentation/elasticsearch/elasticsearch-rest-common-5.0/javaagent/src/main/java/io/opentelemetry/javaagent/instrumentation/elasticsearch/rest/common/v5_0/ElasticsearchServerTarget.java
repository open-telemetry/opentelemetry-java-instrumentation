/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTargetBuilder;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;

public final class ElasticsearchServerTarget {

  @Nullable
  public static ServerTarget of(@Nullable List<HttpHost> hosts) {
    if (hosts == null || hosts.isEmpty()) {
      return null;
    }

    ServerTargetBuilder builder = ServerTarget.builder(-1).setSorted(true);
    for (HttpHost httpHost : hosts) {
      builder.addEndpoint(httpHost.getHostName(), httpHost.getPort(), defaultPort(httpHost));
    }
    return builder.build();
  }

  private static int defaultPort(HttpHost httpHost) {
    if (httpHost.getSchemeName().equalsIgnoreCase("http")) {
      return 80;
    }
    if (httpHost.getSchemeName().equalsIgnoreCase("https")) {
      return 443;
    }
    return -1;
  }

  private ElasticsearchServerTarget() {}
}
