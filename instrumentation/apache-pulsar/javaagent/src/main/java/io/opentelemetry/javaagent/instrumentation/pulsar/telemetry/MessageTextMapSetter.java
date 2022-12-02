/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.telemetry;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.MessageImpl;

class MessageTextMapSetter implements TextMapSetter<Message<?>> {
  public static final TextMapSetter<Message<?>> INSTANCE = new MessageTextMapSetter();

  @Override
  public void set(@Nullable Message<?> carrier, String key, String value) {
    if (carrier instanceof MessageImpl<?>) {
      MessageImpl<?> message = (MessageImpl<?>) carrier;
      message.getMessageBuilder().addProperty().setKey(key).setValue(value);
    }
  }
}
