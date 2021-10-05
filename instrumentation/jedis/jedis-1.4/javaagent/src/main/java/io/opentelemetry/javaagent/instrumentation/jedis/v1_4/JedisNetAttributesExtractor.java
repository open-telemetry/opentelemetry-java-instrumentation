/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JedisNetAttributesExtractor extends NetAttributesServerExtractor<JedisRequest, Void> {

  @Override
  @Nullable
  public String transport(JedisRequest request) {
    return null;
  }

  @Override
  public String peerName(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer peerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }

  @Override
  @Nullable
  public String peerIp(JedisRequest request) {
    return null;
  }
}
