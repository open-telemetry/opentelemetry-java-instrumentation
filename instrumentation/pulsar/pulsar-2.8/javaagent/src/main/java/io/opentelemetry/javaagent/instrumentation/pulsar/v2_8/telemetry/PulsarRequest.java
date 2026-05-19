/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.parseUrl;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.ProducerData;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;

public class PulsarRequest extends BasePulsarRequest {
  private final Message<?> message;
  private final String messageId;

  public static PulsarRequest create(Message<?> message) {
    return new PulsarRequest(message, message.getTopicName(), null);
  }

  public static PulsarRequest create(Message<?> message, @Nullable String url) {
    return new PulsarRequest(message, message.getTopicName(), parseUrl(url));
  }

  public static PulsarRequest create(Message<?> message, @Nullable UrlData urlData) {
    return new PulsarRequest(message, message.getTopicName(), urlData);
  }

  public static PulsarRequest create(Message<?> message, ProducerData producerData) {
    return new PulsarRequest(message, producerData.topic, parseUrl(producerData.url));
  }

  private PulsarRequest(Message<?> message, String destination, @Nullable UrlData urlData) {
    super(destination, urlData);
    this.message = message;
    // for producer spans message id is not available when the PulsarRequest is created, so we will
    // try to get it later when it's available
    MessageId id = message.getMessageId();
    this.messageId = id != null ? id.toString() : null;
  }

  public Message<?> getMessage() {
    return message;
  }

  @Nullable
  public String getMessageId() {
    if (messageId != null) {
      return messageId;
    }
    MessageId id = message.getMessageId();
    return id != null ? id.toString() : null;
  }
}
