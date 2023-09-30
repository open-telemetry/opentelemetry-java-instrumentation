/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

public class Elasticsearch6TransportNetworkAttributesGetter
    implements ServerAttributesGetter<ElasticTransportRequest, ActionResponse> {

  @Override
  @Nullable
  public InetSocketAddress getServerInetSocketAddress(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().address();
    }
    return null;
  }
}
