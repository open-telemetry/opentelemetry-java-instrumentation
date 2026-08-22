/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

class SpringRabbitServerAttributesGetter implements ServerAttributesGetter<SpringRabbitRequest> {

  @Override
  public String getServerAddress(SpringRabbitRequest request) {
    return request.getChannel().getConnection().getAddress().getHostAddress();
  }

  @Override
  public Integer getServerPort(SpringRabbitRequest request) {
    return request.getChannel().getConnection().getPort();
  }
}
