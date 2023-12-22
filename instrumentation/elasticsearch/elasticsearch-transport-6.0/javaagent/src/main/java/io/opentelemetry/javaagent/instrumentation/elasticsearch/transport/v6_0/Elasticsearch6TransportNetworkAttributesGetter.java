/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

public class Elasticsearch6TransportNetworkAttributesGetter
    implements NetworkAttributesGetter<ElasticTransportRequest, ActionResponse> {

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
