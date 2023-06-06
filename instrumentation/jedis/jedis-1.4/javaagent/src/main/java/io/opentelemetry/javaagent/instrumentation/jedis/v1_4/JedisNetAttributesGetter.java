/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;

final class JedisNetAttributesGetter implements NetClientAttributesGetter<JedisRequest, Void> {

  @Override
  public String getServerAddress(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }
}
