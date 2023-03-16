/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.impl.MessageImpl;

enum MessageTextMapSetter implements TextMapSetter<PulsarRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable PulsarRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    if (carrier.getMessage() instanceof MessageImpl<?>) {
      MessageImpl<?> message = (MessageImpl<?>) carrier.getMessage();
      message.getMessageBuilder().addProperty().setKey(key).setValue(value);
    }
  }
}
