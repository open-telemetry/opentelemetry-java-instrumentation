/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.lang.reflect.Field;
import java.util.Map;

public enum PubSubAttributesMapSetter implements TextMapSetter<PubsubMessage> {
  INSTANCE;

  @Override
  public void set(PubsubMessage carrier, String key, String value) {
    try {
      Class cls = carrier.getClass();
      Field attributes = cls.getDeclaredField("attributes_");
      attributes.setAccessible(true);
      Class attributesClass = attributes.get(carrier).getClass();
      Field mapData = attributesClass.getDeclaredField("mapData");
      mapData.setAccessible(true);
      Class mapDataObj = mapData.get(attributes.get(carrier)).getClass();

      Field delegateField = mapDataObj.getDeclaredField("delegate");
      delegateField.setAccessible(true);
      Object delegate = delegateField.get(mapData.get(attributes.get(carrier)));
      Map<String, String> newAttributes = (Map) delegate;
      newAttributes.put(key, value);
    } catch (Exception e) {
      System.out.println("Got Exception while instrumenting pubsubMessage: " + e);
    }
  }
}
