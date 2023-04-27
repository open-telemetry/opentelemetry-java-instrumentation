/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.ProducerData;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import org.apache.pulsar.client.api.Message;

public final class PulsarRequest extends BasePulsarRequest {
  private final Message<?> message;

  private PulsarRequest(Message<?> message, String destination, UrlData urlData) {
    super(destination, urlData);
    this.message = message;
  }

  public static PulsarRequest create(Message<?> message) {
    return new PulsarRequest(message, message.getTopicName(), null);
  }

  public static PulsarRequest create(Message<?> message, String url) {
    return new PulsarRequest(message, message.getTopicName(), parseUrl(url));
  }

  public static PulsarRequest create(Message<?> message, UrlData urlData) {
    return new PulsarRequest(message, message.getTopicName(), urlData);
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url));
  }

  public Message<?> getMessage() {
    return message;
  }
}
