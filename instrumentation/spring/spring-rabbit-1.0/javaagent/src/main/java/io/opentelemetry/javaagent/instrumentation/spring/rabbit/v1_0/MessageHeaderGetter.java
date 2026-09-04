/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

class MessageHeaderGetter implements TextMapGetter<SpringRabbitRequest> {

  @Override
  public Iterable<String> keys(SpringRabbitRequest carrier) {
    return carrier.getMessage().getMessageProperties().getHeaders().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable SpringRabbitRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    Object value = carrier.getMessage().getMessageProperties().getHeaders().get(key);
    return value == null ? null : value.toString();
  }
}
