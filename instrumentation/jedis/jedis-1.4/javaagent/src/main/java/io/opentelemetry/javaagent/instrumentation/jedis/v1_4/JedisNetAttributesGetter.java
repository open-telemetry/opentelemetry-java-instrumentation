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
  public String getTransport(JedisRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public String getPeerName(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer getPeerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }
}
