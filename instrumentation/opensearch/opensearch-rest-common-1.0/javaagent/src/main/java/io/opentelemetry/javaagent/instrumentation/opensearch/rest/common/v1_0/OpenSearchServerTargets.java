/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.opensearch.client.RestClient;

/**
 * Keeps the target a rest client was built with. The nodes a rest client reports later are the ones
 * it currently routes to, which sniffing and {@code setNodes} can replace at any time.
 */
public final class OpenSearchServerTargets {

  private static final VirtualField<RestClient, OpenSearchServerTarget> serverTarget =
      VirtualField.find(RestClient.class, OpenSearchServerTarget.class);

  public static void capture(
      RestClient restClient, @Nullable List<OpenSearchServerTarget.Endpoint> configuredEndpoints) {
    OpenSearchServerTarget target = OpenSearchServerTarget.of(configuredEndpoints);
    if (target != null) {
      serverTarget.set(restClient, target);
    }
  }

  @Nullable
  public static OpenSearchServerTarget get(RestClient restClient) {
    return serverTarget.get(restClient);
  }

  @Nullable
  public static OpenSearchServerTarget getForObject(Object restClient) {
    return restClient instanceof RestClient ? get((RestClient) restClient) : null;
  }

  private OpenSearchServerTargets() {}
}
