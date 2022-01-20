/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

final class JedisNetAttributesGetter implements NetClientAttributesGetter<JedisRequest, Void> {

  @Override
  @Nullable
  public String transport(JedisRequest request, @Nullable Void unused) {
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
