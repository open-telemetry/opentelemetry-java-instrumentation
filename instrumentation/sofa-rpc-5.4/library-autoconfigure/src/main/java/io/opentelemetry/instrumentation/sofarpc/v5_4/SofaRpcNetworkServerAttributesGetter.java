/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class SofaRpcNetworkServerAttributesGetter
    implements NetworkAttributesGetter<SofaRpcRequest, SofaResponse> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    return request.localAddress();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    return request.remoteAddress();
  }
}
