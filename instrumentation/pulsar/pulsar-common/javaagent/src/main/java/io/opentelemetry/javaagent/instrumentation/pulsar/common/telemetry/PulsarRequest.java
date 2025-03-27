/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.common.telemetry;

import io.opentelemetry.javaagent.instrumentation.pulsar.common.ProducerData;
import io.opentelemetry.javaagent.instrumentation.pulsar.common.UrlParser;
import org.apache.pulsar.client.api.Message;

import static io.opentelemetry.javaagent.instrumentation.pulsar.common.UrlParser.parseUrl;

public final class PulsarRequest extends BasePulsarRequest {
  private final Message<?> message;
  private int produceNumMessages;

  private PulsarRequest(Message<?> message, String destination, UrlParser.UrlData urlData) {
    super(destination, urlData);
    this.message = message;
  }

  public static PulsarRequest create(Message<?> message) {
    return new PulsarRequest(message, message.getTopicName(), null);
  }

  public static PulsarRequest create(Message<?> message, String url) {
    return new PulsarRequest(message, message.getTopicName(), parseUrl(url));
  }

  public static PulsarRequest create(Message<?> message, UrlParser.UrlData urlData) {
    return new PulsarRequest(message, message.getTopicName(), urlData);
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url));
  }

  public Message<?> getMessage() {
    return message;
  }


  public int getProduceNumMessages() {
    return produceNumMessages;
  }

  public void setProduceNumMessages(int produceNumMessages) {
    this.produceNumMessages = produceNumMessages;
  }
}
