/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclientjava.v5_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

enum MapSetter implements TextMapSetter<PublishingMessageImpl> {
  INSTANCE;

  @Override
  public void set(@Nullable PublishingMessageImpl message, String key, String value) {
    if (message == null) {
      return;
    }
    VirtualField<PublishingMessageImpl, Map<String, String>> virtualField =
        VirtualField.find(PublishingMessageImpl.class, Map.class);
    Map<String, String> extraProperties = virtualField.get(message);
    if (extraProperties == null) {
      extraProperties = new HashMap<>();
      virtualField.set(message, extraProperties);
    }
    extraProperties.put(key, value);
  }
}
