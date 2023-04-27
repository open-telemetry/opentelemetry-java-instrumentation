/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

enum MessageTextMapGetter implements TextMapGetter<PulsarRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(PulsarRequest request) {
    return request.getMessage().getProperties().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable PulsarRequest request, String key) {
    return request == null ? null : request.getMessage().getProperties().get(key);
  }
}
