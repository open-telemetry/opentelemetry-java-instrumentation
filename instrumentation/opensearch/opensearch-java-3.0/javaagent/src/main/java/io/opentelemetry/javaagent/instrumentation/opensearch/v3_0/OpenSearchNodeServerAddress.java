/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import java.util.List;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.transport.httpclient5.internal.Node;

public class OpenSearchNodeServerAddress {

  @Nullable
  public static OpenSearchServerAddress fromApacheNodes(List<Node> nodes) {
    if (nodes.size() != 1) {
      return null;
    }
    HttpHost host = nodes.get(0).getHost();
    return OpenSearchServerAddress.create(host.getHostName(), host.getPort());
  }

  @Nullable
  static OpenSearchServerAddress fromRestClientNodes(Object restClient) {
    try {
      // getNodes() returns a list of org.opensearch.client.Node, which comes from
      // opensearch-rest-client and must not be referenced statically in the Muzzle helper graph,
      // so the nodes are read reflectively
      List<?> nodes = (List<?>) restClient.getClass().getMethod("getNodes").invoke(restClient);
      if (nodes.size() != 1) {
        return null;
      }
      Object node = nodes.get(0);
      Object host = node.getClass().getMethod("getHost").invoke(node);
      String hostName = (String) host.getClass().getMethod("getHostName").invoke(host);
      int port = (Integer) host.getClass().getMethod("getPort").invoke(host);
      return OpenSearchServerAddress.create(hostName, port);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private OpenSearchNodeServerAddress() {}
}
