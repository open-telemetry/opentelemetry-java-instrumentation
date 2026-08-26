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

// keep the initial target separate from the mutable routing hosts
public class ElasticsearchServerTargets {

  private static final VirtualField<RestClient, ElasticsearchServerTarget> SERVER_TARGET =
      VirtualField.find(RestClient.class, ElasticsearchServerTarget.class);

  public static void capture(RestClient restClient, @Nullable List<HttpHost> configuredHosts) {
    ElasticsearchServerTarget target = ElasticsearchServerTarget.of(configuredHosts);
    if (target != null) {
      SERVER_TARGET.set(restClient, target);
    }
  }

  @Nullable
  public static ElasticsearchServerTarget get(RestClient restClient) {
    return SERVER_TARGET.get(restClient);
  }

  private ElasticsearchServerTargets() {}
}
