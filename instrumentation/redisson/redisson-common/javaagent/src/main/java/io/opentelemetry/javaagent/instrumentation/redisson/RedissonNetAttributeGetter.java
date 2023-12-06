/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributeGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class RedissonNetAttributeGetter implements NetworkAttributeGetter<RedissonRequest, Void> {

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }
}
