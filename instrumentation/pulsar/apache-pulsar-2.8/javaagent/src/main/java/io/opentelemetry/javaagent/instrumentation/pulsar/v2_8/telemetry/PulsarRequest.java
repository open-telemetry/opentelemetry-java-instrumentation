/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.ProducerData;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import org.apache.pulsar.client.api.Message;

public final class PulsarRequest {
  private final Message<?> message;
  private final String destination;
  private final UrlData urlData;

  private PulsarRequest(Message<?> message, String destination, UrlData urlData) {
    this.message = message;
    this.destination = destination;
    this.urlData = urlData;
  }

  public static PulsarRequest create(Message<?> message) {
    return new PulsarRequest(message, message.getTopicName(), null);
  }

  public static PulsarRequest create(Message<?> message, String url) {
    return new PulsarRequest(message, message.getTopicName(), parseUrl(url));
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url));
  }

  public Message<?> getMessage() {
    return message;
  }

  public String getDestination() {
    return destination;
  }

  public UrlData getUrlData() {
    return urlData;
  }
}
