/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesExtractor;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class RedissonNetAttributesExtractor
    extends InetSocketAddressNetClientAttributesExtractor<RedissonRequest, Void> {

  @Override
  public InetSocketAddress getAddress(RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }

  @Nullable
  @Override
  public String transport(RedissonRequest request, @Nullable Void unused) {
    return null;
  }
}
