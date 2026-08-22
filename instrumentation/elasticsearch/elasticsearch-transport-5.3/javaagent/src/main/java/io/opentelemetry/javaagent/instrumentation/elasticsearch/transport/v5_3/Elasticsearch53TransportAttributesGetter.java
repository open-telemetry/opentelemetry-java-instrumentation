/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportAttributesGetter;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.transport.TransportAddress;

final class Elasticsearch53TransportAttributesGetter
    extends ElasticsearchTransportAttributesGetter {

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

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ElasticTransportRequest request,
      @Nullable ActionResponse response,
      @Nullable Throwable error) {
    if (response == null) {
      return;
    }

    TransportAddress remoteAddress = response.remoteAddress();
    if (remoteAddress == null) {
      return;
    }

    String serverAddress = remoteAddress.getAddress();
    attributes.put(SERVER_ADDRESS, serverAddress);
    int serverPort = remoteAddress.getPort();
    if (serverPort > 0) {
      attributes.put(SERVER_PORT, serverPort);
    }
    updateStableSpanName(context, request, serverAddress, serverPort);
  }
}
