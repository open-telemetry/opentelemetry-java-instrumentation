/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

/**
 * for producer and consumer, cache service_url and determine whether the instance has been
 * enhanced.
 */
class ClientEnhanceInfo {
  public static final String DEFAULT_TOPIC = "unknown";
  public static final String DEFAULT_BROKER_URL = "unknown";

  public final String topic;
  public final String brokerUrl;

  private ClientEnhanceInfo(String topic, String brokerUrl) {
    this.topic = isNullOrEmpty(topic) ? DEFAULT_TOPIC : topic;
    this.brokerUrl = isNullOrEmpty(brokerUrl) ? DEFAULT_BROKER_URL : brokerUrl;
  }

  public static ClientEnhanceInfo create(String topic, String brokerUrl) {
    return new ClientEnhanceInfo(topic, brokerUrl);
  }

  private static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
