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
  private static final VirtualField<Producer<?>, ClientEnhanceInfo> PRODUCER_ENHANCED_FIELD =
      VirtualField.find(Producer.class, ClientEnhanceInfo.class);
  private static final VirtualField<Consumer<?>, ClientEnhanceInfo> CONSUMER_ENHANCED_FIELD =
      VirtualField.find(Consumer.class, ClientEnhanceInfo.class);

  public final String topic;
  public final String brokerUrl;

  public ClientEnhanceInfo(String topic, String brokerUrl) {
    this.topic = topic;
    this.brokerUrl = brokerUrl;
  }

  public static void setProducerEnhancedField(Producer<?> instance, ClientEnhanceInfo info) {
    PRODUCER_ENHANCED_FIELD.set(instance, info);
  }

  public static ClientEnhanceInfo getProducerEnhancedField(Producer<?> instance) {
    return PRODUCER_ENHANCED_FIELD.get(instance);
  }

  public static void setConsumerEnhancedField(Consumer<?> instance, ClientEnhanceInfo info) {
    CONSUMER_ENHANCED_FIELD.set(instance, info);
  }

  public static ClientEnhanceInfo getConsumerEnhancedField(Consumer<?> instance) {
    return CONSUMER_ENHANCED_FIELD.get(instance);
  }
}
