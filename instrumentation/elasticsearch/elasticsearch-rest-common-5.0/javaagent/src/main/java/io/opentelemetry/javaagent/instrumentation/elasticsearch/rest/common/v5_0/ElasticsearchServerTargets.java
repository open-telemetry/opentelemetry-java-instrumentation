/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

// keep the initial target separate from the mutable routing hosts
public class ElasticsearchServerTargets {

  private static final VirtualField<RestClient, ServerTarget> SERVER_TARGET =
      VirtualField.find(RestClient.class, ServerTarget.class);

  public static void capture(RestClient restClient, @Nullable List<HttpHost> configuredHosts) {
    ServerTarget target = ElasticsearchServerTarget.of(configuredHosts);
    if (target != null) {
      SERVER_TARGET.set(restClient, target);
    }
  }

  @Nullable
  public static ServerTarget get(RestClient restClient) {
    return SERVER_TARGET.get(restClient);
  }

  private ElasticsearchServerTargets() {}
}
