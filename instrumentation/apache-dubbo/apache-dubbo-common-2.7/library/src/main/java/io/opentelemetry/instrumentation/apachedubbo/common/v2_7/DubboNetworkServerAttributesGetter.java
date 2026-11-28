/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.common.v2_7;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

final class DubboNetworkServerAttributesGetter
    implements NetworkAttributesGetter<DubboRequest, Result>,
        AttributesExtractor<DubboRequest, Result> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, DubboRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      DubboRequest request,
      @Nullable Result result,
      @Nullable Throwable error) {
    InetSocketAddress address = request.localAddress();
    // The shared network extractor omits the port when the address is unresolved.
    if (request.rpcMethod() != null
        && address != null
        && address.isUnresolved()
        && address.getPort() > 0) {
      attributes.put(NETWORK_LOCAL_PORT, (long) address.getPort());
    }
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.localAddress();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.remoteAddress();
  }
}
