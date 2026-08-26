/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTargets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

// read optional opensearch-rest-client nodes reflectively to keep other transports independent
public class OpenSearchRestClientNodes {

  @Nullable
  public static OpenSearchServerTarget target(Object restClient) {
    OpenSearchServerTarget capturedTarget = OpenSearchServerTargets.getForObject(restClient);
    if (capturedTarget != null) {
      return capturedTarget;
    }
    try {
      List<?> nodes = (List<?>) restClient.getClass().getMethod("getNodes").invoke(restClient);
      List<OpenSearchServerTarget.Endpoint> endpoints = new ArrayList<>(nodes.size());
      for (Object node : nodes) {
        Object host = node.getClass().getMethod("getHost").invoke(node);
        endpoints.add(
            new OpenSearchServerTarget.Endpoint(
                (String) host.getClass().getMethod("getHostName").invoke(host),
                (Integer) host.getClass().getMethod("getPort").invoke(host)));
      }
      return OpenSearchServerTarget.of(endpoints);
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      return null;
    }
  }

  private OpenSearchRestClientNodes() {}
}
