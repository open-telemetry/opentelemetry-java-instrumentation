/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.PulsarConfigs.METRICS_CONFIG_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public class PulsarMetricsUtil {

  public static final String SCOPE_NAME = "io.opentelemetry.pulsar-clients-java-2.8";
  public static final String PULSAR_CLIENT_PREFIX = "pulsar.client.";
  public static final String PRODUCER_METRICS_PREFIX = PULSAR_CLIENT_PREFIX + "producer.";
  public static final String CONSUMER_METRICS_PREFIX = PULSAR_CLIENT_PREFIX + "consumer.";
  public static final AttributeKey<String> ATTRIBUTE_TOPIC = AttributeKey.stringKey("topic");
  public static final AttributeKey<String> ATTRIBUTE_SUBSCRIPTION = AttributeKey.stringKey(
      "subscription");
  public static final AttributeKey<String> ATTRIBUTE_PRODUCER_NAME = AttributeKey.stringKey(
      "producer.name");
  public static final AttributeKey<String> ATTRIBUTE_CONSUMER_NAME = AttributeKey.stringKey(
      "consumer.name");
  public static final AttributeKey<String> ATTRIBUTE_QUANTILE = AttributeKey.stringKey("quantile");
  public static final AttributeKey<String> ATTRIBUTE_RESPONSE_STATUS = AttributeKey.stringKey(
      "response.status");

  private static volatile PulsarMetricsRegistry metricsRegistry;

  public static synchronized PulsarMetricsRegistry getMetricsRegistry() {
    if (metricsRegistry == null) {
      synchronized (PulsarMetricsUtil.class) {
        if (metricsRegistry == null) {
          if (InstrumentationConfig.get().getBoolean(METRICS_CONFIG_NAME, true)) {
            metricsRegistry = new PulsarMetricsRegistry();
            metricsRegistry.init();
          } else {
            metricsRegistry = PulsarMetricsRegistry.PulsarMetricsRegistryDisabled.INSTANCE;
          }
        }
      }
    }
    return metricsRegistry;
  }

  private PulsarMetricsUtil() {
  }
}
