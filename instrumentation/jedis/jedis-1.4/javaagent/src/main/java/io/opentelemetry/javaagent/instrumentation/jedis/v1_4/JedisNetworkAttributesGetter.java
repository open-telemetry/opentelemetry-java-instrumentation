/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

final class JedisNetworkAttributesGetter implements ServerAttributesGetter<JedisRequest> {

  @Override
  public String getServerAddress(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }
}
