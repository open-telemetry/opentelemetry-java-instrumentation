/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.KeyValue;
import org.apache.pulsar.common.api.proto.MessageMetadata;

final class MessageTextMapSetter implements TextMapSetter<PulsarRequest> {

  @Override
  public void set(@Nullable PulsarRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    if (carrier.getMessage() instanceof MessageImpl<?>) {
      MessageImpl<?> message = (MessageImpl<?>) carrier.getMessage();
      MessageMetadata metadata = message.getMessageBuilder();
      boolean updated = false;
      // A replacement message may copy properties already injected before the interceptor ran.
      for (KeyValue property : metadata.getPropertiesList()) {
        if (property.getKey().equals(key)) {
          property.setValue(value);
          updated = true;
        }
      }
      if (!updated) {
        metadata.addProperty().setKey(key).setValue(value);
      }
    }
  }
}
