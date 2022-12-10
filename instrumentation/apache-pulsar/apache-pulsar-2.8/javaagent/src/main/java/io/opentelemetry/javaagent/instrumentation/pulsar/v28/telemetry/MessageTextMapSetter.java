/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.MessageImpl;

enum MessageTextMapSetter implements TextMapSetter<Message<?>> {
  INSTANCE;

  @Override
  public void set(@Nullable Message<?> carrier, String key, String value) {
    if (carrier instanceof MessageImpl<?>) {
      MessageImpl<?> message = (MessageImpl<?>) carrier;
      message.getMessageBuilder().addProperty().setKey(key).setValue(value);
    }
  }
}
