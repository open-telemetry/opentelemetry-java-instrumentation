/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class ThriftServerNetworkAttributesGetter
    implements NetworkAttributesGetter<ThriftRequest, ThriftResponse> {

  @Nullable
  @Override
  public SocketAddress getNetworkLocalSocketAddress(
      ThriftRequest request, @Nullable ThriftResponse response) {
    return request.getLocalAddress();
  }

  @Override
  @Nullable
  public SocketAddress getNetworkPeerSocketAddress(
      ThriftRequest request, @Nullable ThriftResponse response) {
    return request.getRemoteAddress();
  }
}
