/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesOnStartExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class RedissonNetAttributesExtractor
    extends InetSocketAddressNetAttributesOnStartExtractor<RedissonRequest, Void> {

  @Override
  public InetSocketAddress getAddress(RedissonRequest request) {
    return request.getAddress();
  }

  @Nullable
  @Override
  public String transport(RedissonRequest request) {
    return null;
  }
}
