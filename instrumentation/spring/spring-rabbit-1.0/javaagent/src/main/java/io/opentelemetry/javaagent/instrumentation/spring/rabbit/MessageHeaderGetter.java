/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

enum MessageHeaderGetter implements TextMapGetter<Message> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Message carrier) {
    return carrier.getMessageProperties().getHeaders().keySet();
  }

  @Nullable
  @Override
  public String get(Message carrier, String key) {
    Object value = carrier.getMessageProperties().getHeaders().get(key);
    return value == null ? null : value.toString();
  }
}
