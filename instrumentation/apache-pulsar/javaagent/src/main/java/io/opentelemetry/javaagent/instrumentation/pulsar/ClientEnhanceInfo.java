/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import com.google.common.base.Strings;

/**
 * for producer and consumer, cache service_url and determine whether the instance has been
 * enhanced.
 */
class ClientEnhanceInfo {
  public static final String DEFAULT_TOPIC = "unknown";
  public static final String DEFAULT_BROKER_URL = "unknown";

  public final String topic;
  public final String brokerURL;

  private ClientEnhanceInfo(String topic, String brokerURL) {
    this.topic = Strings.isNullOrEmpty(topic) ? DEFAULT_TOPIC : topic;
    this.brokerURL = Strings.isNullOrEmpty(brokerURL) ? DEFAULT_BROKER_URL : brokerURL;
  }

  public static ClientEnhanceInfo create(String topic, String brokerURL) {
    return new ClientEnhanceInfo(topic, brokerURL);
  }
}
