/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

enum DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest> {
  INSTANCE;

  @Override
  public String getSystem(DubboRequest request) {
    return "apache_dubbo";
  }

  @Override
  public String getService(DubboRequest request) {
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Override
  public String getMethod(DubboRequest request) {
    return request.invocation().getMethodName();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      DubboRequest dubboRequest, @Nullable Object o) {
    return dubboRequest.localAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      DubboRequest dubboRequest, @Nullable Object o) {
    return dubboRequest.remoteAddress();
  }
}
