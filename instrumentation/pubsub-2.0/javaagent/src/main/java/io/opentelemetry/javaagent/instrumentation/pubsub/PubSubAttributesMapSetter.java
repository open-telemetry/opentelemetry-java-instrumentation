/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Map;
import java.util.Optional;

public enum PubSubAttributesMapSetter implements TextMapSetter<PubsubMessage> {
  INSTANCE;

  @Override
  public void set(PubsubMessage carrier, String key, String value) {
    Object carrierAsMap = PubsubSingletons.extractPubsubMessageAttributes(carrier);
    if (!(carrierAsMap instanceof Optional)) {
      Map<String, String> newAttributes = (Map) carrierAsMap;
      newAttributes.put(key, value);
    }
  }
}
