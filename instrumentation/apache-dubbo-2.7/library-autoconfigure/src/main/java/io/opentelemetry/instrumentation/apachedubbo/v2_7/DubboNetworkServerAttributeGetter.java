/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributeGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboNetworkServerAttributeGetter
    implements NetworkAttributeGetter<DubboRequest, Result> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.localAddress();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.remoteAddress();
  }
}
