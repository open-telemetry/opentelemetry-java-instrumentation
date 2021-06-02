/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import com.google.auto.value.AutoValue;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@AutoValue
public abstract class MessageWithChannel {

  public abstract Message<?> getMessage();

  public abstract MessageChannel getMessageChannel();

  static MessageWithChannel create(Message<?> message, MessageChannel messageChannel) {
    return new AutoValue_MessageWithChannel(message, messageChannel);
  }
}
