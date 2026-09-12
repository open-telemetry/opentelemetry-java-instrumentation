/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.opensearch.client.RestClient;

// keep the initial target separate from the mutable routing nodes
public class OpenSearchServerTargets {

  private static final VirtualField<RestClient, DbServerTarget> SERVER_TARGET =
      VirtualField.find(RestClient.class, DbServerTarget.class);

  public static void capture(
      RestClient restClient, @Nullable List<OpenSearchServerTarget.Endpoint> configuredEndpoints) {
    DbServerTarget target = OpenSearchServerTarget.of(configuredEndpoints);
    if (target != null) {
      SERVER_TARGET.set(restClient, target);
    }
  }

  @Nullable
  public static DbServerTarget get(RestClient restClient) {
    return SERVER_TARGET.get(restClient);
  }

  private OpenSearchServerTargets() {}
}
