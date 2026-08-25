/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchServerTarget;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Keeps the target a rest client was built with. The hosts a rest client reports later are the ones
 * it currently routes to, which sniffing and {@code setNodes} can replace at any time.
 */
public final class ElasticsearchServerTargets {

  private static final VirtualField<RestClient, ElasticsearchServerTarget> serverTarget =
      VirtualField.find(RestClient.class, ElasticsearchServerTarget.class);

  public static void capture(RestClient restClient, @Nullable List<HttpHost> configuredHosts) {
    ElasticsearchServerTarget target = ElasticsearchServerTarget.of(configuredHosts);
    if (target != null) {
      serverTarget.set(restClient, target);
    }
  }

  @Nullable
  public static ElasticsearchServerTarget get(RestClient restClient) {
    return serverTarget.get(restClient);
  }

  private ElasticsearchServerTargets() {}
}
