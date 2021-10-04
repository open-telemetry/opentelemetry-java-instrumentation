/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JedisNetAttributesExtractor extends NetAttributesExtractor<JedisRequest, Void> {

  JedisNetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_START);
  }

  @Override
  @Nullable
  public String transport(JedisRequest request) {
    return null;
  }

  @Override
  public String peerName(JedisRequest request, @Nullable Void unused) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer peerPort(JedisRequest request, @Nullable Void unused) {
    return request.getConnection().getPort();
  }

  @Override
  @Nullable
  public String peerIp(JedisRequest request, @Nullable Void unused) {
    return null;
  }
}
