/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

@AutoValue
public abstract class SofaRpcRequest {

  static SofaRpcRequest create(SofaRequest request) {
    RpcInternalContext context = RpcInternalContext.getContext();

    // Get network addresses from RpcInternalContext
    InetSocketAddress remoteAddress = context != null ? context.getRemoteAddress() : null;
    InetSocketAddress localAddress = context != null ? context.getLocalAddress() : null;

    return new AutoValue_SofaRpcRequest(request, remoteAddress, localAddress);
  }

  public abstract SofaRequest request();

  @Nullable
  public abstract InetSocketAddress remoteAddress();

  @Nullable
  public abstract InetSocketAddress localAddress();
}
