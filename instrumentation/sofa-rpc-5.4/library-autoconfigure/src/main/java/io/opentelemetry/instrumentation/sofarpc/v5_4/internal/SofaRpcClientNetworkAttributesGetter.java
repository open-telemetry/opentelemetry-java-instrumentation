/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4.internal;

import com.alipay.sofa.rpc.client.ProviderInfo;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.sofarpc.v5_4.SofaRpcRequest;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SofaRpcClientNetworkAttributesGetter
    implements ServerAttributesGetter<SofaRpcRequest>,
        NetworkAttributesGetter<SofaRpcRequest, SofaResponse> {

  @Nullable
  @Override
  public String getServerAddress(SofaRpcRequest request) {
    ProviderInfo providerInfo = request.providerInfo();
    if (providerInfo != null) {
      return providerInfo.getHost();
    }
    return null;
  }

  @Override
  @Nullable
  public Integer getServerPort(SofaRpcRequest request) {
    ProviderInfo providerInfo = request.providerInfo();
    if (providerInfo != null) {
      return providerInfo.getPort();
    }
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    return request.remoteAddress();
  }
}
