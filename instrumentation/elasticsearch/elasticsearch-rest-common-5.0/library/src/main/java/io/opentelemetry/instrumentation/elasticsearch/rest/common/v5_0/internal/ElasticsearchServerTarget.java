/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ElasticsearchServerTarget {

  @Nullable
  public static DbServerTarget of(@Nullable List<HttpHost> hosts) {
    if (hosts == null || hosts.isEmpty()) {
      return null;
    }

    DbServerTargetBuilder builder = DbServerTarget.builder(-1).setSorted(true);
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
