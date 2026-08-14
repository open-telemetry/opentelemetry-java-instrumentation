/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.ProducerData;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;

public class PulsarRequest extends BasePulsarRequest {
  private final Message<?> message;

  public static PulsarRequest create(Message<?> message, Consumer<?> consumer) {
    return new PulsarRequest(message, message.getTopicName(), null, consumer.getSubscription());
  }

  public static PulsarRequest create(
      Message<?> message, @Nullable String url, Consumer<?> consumer) {
    return new PulsarRequest(
        message, message.getTopicName(), parseUrl(url), consumer.getSubscription());
  }

  public static PulsarRequest create(
      Message<?> message, @Nullable UrlData urlData, @Nullable String subscription) {
    return new PulsarRequest(message, message.getTopicName(), urlData, subscription);
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url), null);
  }

  private PulsarRequest(
      Message<?> message,
      String topicName,
      @Nullable UrlData urlData,
      @Nullable String subscription) {
    super(topicName, urlData, subscription);
    this.message = message;
  }

  public Message<?> getMessage() {
    return message;
  }
}
