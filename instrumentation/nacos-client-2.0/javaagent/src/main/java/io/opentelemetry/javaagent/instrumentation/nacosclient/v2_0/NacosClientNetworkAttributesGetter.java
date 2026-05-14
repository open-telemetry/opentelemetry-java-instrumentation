/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

final class NacosClientNetworkAttributesGetter
    implements ServerAttributesGetter<NacosClientRequest>,
        NetworkAttributesGetter<NacosClientRequest, Response> {

  @Override
  @Nullable
  public String getServerAddress(NacosClientRequest request) {
    String peerHost = request.peerHost();
    return peerHost != null ? peerHost : request.peerAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(NacosClientRequest request) {
    return request.peerPort();
  }

  @Override
  @Nullable
  public String getNetworkTransport(NacosClientRequest request, @Nullable Response response) {
    return "tcp";
  }

  @Override
  @Nullable
  public String getNetworkProtocolName(NacosClientRequest request, @Nullable Response response) {
    return "grpc";
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(NacosClientRequest request, @Nullable Response response) {
    return request.peerHost();
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(NacosClientRequest request, @Nullable Response response) {
    return request.peerPort();
  }
}
