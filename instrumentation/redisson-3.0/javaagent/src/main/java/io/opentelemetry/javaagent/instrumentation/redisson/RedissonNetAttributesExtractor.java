/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class RedissonNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<RedissonRequest, Void> {

  @Override
  protected InetSocketAddress getAddress(RedissonRequest request, @Nullable Void unused) {
    return request.getAddress();
  }

  @Nullable
  @Override
  protected String transport(RedissonRequest request) {
    return null;
  }
}
