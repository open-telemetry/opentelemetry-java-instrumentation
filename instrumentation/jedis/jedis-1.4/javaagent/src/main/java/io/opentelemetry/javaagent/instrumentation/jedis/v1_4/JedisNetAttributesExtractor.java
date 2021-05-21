/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JedisNetAttributesExtractor extends NetAttributesExtractor<JedisRequest, Void> {

  @Override
  @Nullable
  protected String transport(JedisRequest request) {
    return null;
  }

  @Override
  protected String peerName(JedisRequest request, @Nullable Void response) {
    return request.getConnection().getHost();
  }

  @Override
  protected Integer peerPort(JedisRequest request, @Nullable Void response) {
    return request.getConnection().getPort();
  }

  @Override
  @Nullable
  protected String peerIp(JedisRequest request, @Nullable Void response) {
    return null;
  }
}
