/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.info;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;

public class ClientEnhanceInfo {
  private final String topic;
  private final String url;

  public ClientEnhanceInfo(String topic, String url) {
    this.topic = topic;
    this.url = url;
  }

  public String getTopic() {
    return topic;
  }

  public String getUrl() {
    return url;
  }

  public static void virtualField(Producer<?> instance, ClientEnhanceInfo info) {
    VirtualField.find(Producer.class, ClientEnhanceInfo.class).set(instance, info);
  }

  public static ClientEnhanceInfo virtualField(Producer<?> instance) {
    return VirtualField.find(Producer.class, ClientEnhanceInfo.class).get(instance);
  }

  public static void virtualField(Consumer<?> instance, ClientEnhanceInfo info) {
    VirtualField.find(Consumer.class, ClientEnhanceInfo.class).set(instance, info);
  }

  public static ClientEnhanceInfo virtualField(Consumer<?> instance) {
    return VirtualField.find(Consumer.class, ClientEnhanceInfo.class).get(instance);
  }
}
