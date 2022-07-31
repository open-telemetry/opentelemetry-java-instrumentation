/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public enum PubSubAttributesMapSetter implements TextMapSetter<PubsubMessage> {
  INSTANCE;

  @Override
  public void set(PubsubMessage carrier, String key, String value) {
    Optional<Object> carrierAsMap = PubsubSingletons.extractPubsubMessageAttributes(carrier);
    if(carrierAsMap.isPresent()) {
      Map<String, String> newAttributes = (Map) carrierAsMap.get();
      newAttributes.put(key, value);
    }

  }



}
