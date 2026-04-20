/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

final class Elasticsearch6TransportAttributesGetter extends ElasticsearchTransportAttributesGetter {

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().address();
    }
    return null;
  }
}
