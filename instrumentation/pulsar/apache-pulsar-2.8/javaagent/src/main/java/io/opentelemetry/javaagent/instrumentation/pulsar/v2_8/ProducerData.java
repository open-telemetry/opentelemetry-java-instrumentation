/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

public final class ProducerData {
  public final String url;
  public final String topic;

  private ProducerData(String url, String topic) {
    this.url = url;
    this.topic = topic;
  }

  public static ProducerData create(String url, String topic) {
    return new ProducerData(url, topic);
  }
}
