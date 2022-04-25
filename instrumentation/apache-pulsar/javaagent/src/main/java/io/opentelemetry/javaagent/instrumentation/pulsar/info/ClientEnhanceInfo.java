/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.info;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import org.apache.pulsar.client.impl.ConsumerImpl;
import org.apache.pulsar.client.impl.ProducerImpl;

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

  public static void virtualField(ProducerImpl<?> instance, ClientEnhanceInfo info) {
    VirtualField.find(ProducerImpl.class, ClientEnhanceInfo.class).set(instance, info);
  }

  public static ClientEnhanceInfo virtualField(ProducerImpl<?> instance) {
    return VirtualField.find(ProducerImpl.class, ClientEnhanceInfo.class).get(instance);
  }

  public static void virtualField(ConsumerImpl<?> instance, ClientEnhanceInfo info) {
    VirtualField.find(ConsumerImpl.class, ClientEnhanceInfo.class).set(instance, info);
  }

  public static ClientEnhanceInfo virtualField(ConsumerImpl<?> instance) {
    return VirtualField.find(ConsumerImpl.class, ClientEnhanceInfo.class).get(instance);
  }
}
