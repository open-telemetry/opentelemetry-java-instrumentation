/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.info;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;

/**
 * for producer and consumer, cache service_url and determine whether the instance has been
 * enhanced.
 */
public class ClientEnhanceInfo {
  public final String topic;
  public final String brokerUrl;

  public ClientEnhanceInfo(String topic, String brokerUrl) {
    this.topic = topic;
    this.brokerUrl = brokerUrl;
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
