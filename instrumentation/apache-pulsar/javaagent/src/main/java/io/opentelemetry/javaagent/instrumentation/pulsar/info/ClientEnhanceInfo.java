/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.info;

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
}
