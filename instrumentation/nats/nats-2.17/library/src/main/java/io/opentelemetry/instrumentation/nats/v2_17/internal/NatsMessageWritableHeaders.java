/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NatsMessageWritableHeaders {

  public static Message create(String subject, byte[] body) {
    return NatsMessage.builder().subject(subject).headers(new Headers()).data(body).build();
  }

  public static Message create(String subject, String replyTo, byte[] body) {
    return NatsMessage.builder()
        .subject(subject)
        .replyTo(replyTo)
        .headers(new Headers())
        .data(body)
        .build();
  }

  public static Message create(String subject, Headers headers, byte[] body) {
    if (headers == null || headers.isReadOnly()) {
      headers = new Headers(headers);
    }
    return NatsMessage.builder().subject(subject).headers(headers).data(body).build();
  }

  public static Message create(String subject, String replyTo, Headers headers, byte[] body) {
    if (headers == null || headers.isReadOnly()) {
      headers = new Headers(headers);
    }
    return NatsMessage.builder()
        .subject(subject)
        .replyTo(replyTo)
        .headers(headers)
        .data(body)
        .build();
  }

  public static Message create(Message message) {
    if (message instanceof NatsMessage
        && (!message.hasHeaders() || message.getHeaders().isReadOnly())) {
      return NatsMessage.builder()
          .subject(message.getSubject())
          .replyTo(message.getReplyTo())
          .headers(new Headers(message.getHeaders()))
          .data(message.getData())
          .build();
    }
    return message;
  }

  private NatsMessageWritableHeaders() {}
}
