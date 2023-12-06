/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributeGetter;

final class JedisNetworkAttributeGetter implements ServerAttributeGetter<JedisRequest> {

  @Override
  public String getServerAddress(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }
}
