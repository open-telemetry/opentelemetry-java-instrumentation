/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.textmap;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.impl.MessageImpl;

public final class MessageTextMapSetter implements TextMapSetter<MessageImpl<?>> {
  public static final TextMapSetter<MessageImpl<?>> INSTANCE = new MessageTextMapSetter();

  @Override
  public void set(@Nullable MessageImpl<?> carrier, String key, String value) {
    if (carrier != null) {
      carrier.getMessageBuilder().addProperty().setKey(key).setValue(value);
    }
  }
}
