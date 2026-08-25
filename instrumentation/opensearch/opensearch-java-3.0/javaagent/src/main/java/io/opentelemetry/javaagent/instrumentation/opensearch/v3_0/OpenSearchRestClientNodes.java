/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Reads the nodes of an {@code org.opensearch.client.RestClient}.
 *
 * <p>The rest client comes from {@code opensearch-rest-client}, which the opensearch java client
 * does not depend on, so it is read reflectively and the instrumentation of the other transports
 * stays available when that artifact is missing.
 */
public final class OpenSearchRestClientNodes {

  @Nullable
  public static OpenSearchServerTarget target(Object restClient) {
    try {
      List<?> nodes = (List<?>) restClient.getClass().getMethod("getNodes").invoke(restClient);
      List<OpenSearchServerTarget.Endpoint> endpoints = new ArrayList<>(nodes.size());
      for (Object node : nodes) {
        Object host = node.getClass().getMethod("getHost").invoke(node);
        endpoints.add(
            new OpenSearchServerTarget.Endpoint(
                (String) host.getClass().getMethod("getSchemeName").invoke(host),
                (String) host.getClass().getMethod("getHostName").invoke(host),
                (Integer) host.getClass().getMethod("getPort").invoke(host)));
      }
      return OpenSearchServerTarget.of(endpoints);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private OpenSearchRestClientNodes() {}
}
