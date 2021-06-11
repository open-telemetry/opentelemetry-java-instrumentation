/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.MessageChannel;

final class MessageChannelSpanNameExtractor implements SpanNameExtractor<MessageWithChannel> {
  @Override
  public String extract(MessageWithChannel messageWithChannel) {
    MessageChannel channel = messageWithChannel.getMessageChannel();
    if (channel instanceof AbstractMessageChannel) {
      return ((AbstractMessageChannel) channel).getFullChannelName();
    }
    if (channel instanceof org.springframework.messaging.support.AbstractMessageChannel) {
      return ((org.springframework.messaging.support.AbstractMessageChannel) channel).getBeanName();
    }
    return channel.getClass().getSimpleName();
  }
}
