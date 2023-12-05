/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

public class ElasticTransportNetworkAttributesGetter
    implements NetworkAttributesGetter<ElasticTransportRequest, ActionResponse> {

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
