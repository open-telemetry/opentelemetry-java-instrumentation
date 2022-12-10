/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;

enum MessageTextMapGetter implements TextMapGetter<Message<?>> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Message<?> message) {
    return message.getProperties().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable Message<?> message, String key) {
    return null == message ? null : message.getProperties().get(key);
  }
}
