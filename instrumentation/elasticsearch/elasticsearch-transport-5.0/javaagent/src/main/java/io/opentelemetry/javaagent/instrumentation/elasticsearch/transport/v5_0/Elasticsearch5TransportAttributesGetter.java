/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportAttributesGetter;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

final class Elasticsearch5TransportAttributesGetter extends ElasticsearchTransportAttributesGetter {

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getAddress();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getPort();
    }
    return null;
  }
}
