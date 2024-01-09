/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing.OpenTelemetryEndpoint;
import javax.annotation.Nullable;

class LettuceServerAttributesGetter implements ServerAttributesGetter<OpenTelemetryEndpoint> {

  @Nullable
  @Override
  public String getServerAddress(OpenTelemetryEndpoint request) {
    return request.address.getHostName();
  }

  @Nullable
  @Override
  public Integer getServerPort(OpenTelemetryEndpoint request) {
    return request.address.getPort();
  }
}
