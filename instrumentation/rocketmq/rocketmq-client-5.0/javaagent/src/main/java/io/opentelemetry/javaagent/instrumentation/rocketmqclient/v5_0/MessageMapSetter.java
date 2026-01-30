/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

enum MessageMapSetter implements TextMapSetter<PublishingMessageImpl> {
  INSTANCE;

  @Override
  public void set(@Nullable PublishingMessageImpl message, String key, String value) {
    if (message == null) {
      return;
    }
    Map<String, String> extraProperties = VirtualFieldStore.getExtraPropertiesByMessage(message);
    if (extraProperties == null) {
      extraProperties = new HashMap<>();
      VirtualFieldStore.setExtraPropertiesByMessage(message, extraProperties);
    }
    extraProperties.put(key, value);
  }
}
