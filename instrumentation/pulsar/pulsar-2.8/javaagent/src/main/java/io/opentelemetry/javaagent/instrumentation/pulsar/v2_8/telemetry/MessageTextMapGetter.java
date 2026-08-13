/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static java.util.Collections.emptySet;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

enum MessageTextMapGetter implements TextMapGetter<PulsarRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(PulsarRequest request) {
    return request.hasMessage() ? request.getMessage().getProperties().keySet() : emptySet();
  }

  @Nullable
  @Override
  public String get(@Nullable PulsarRequest request, String key) {
    return request == null || !request.hasMessage()
        ? null
        : request.getMessage().getProperties().get(key);
  }
}
