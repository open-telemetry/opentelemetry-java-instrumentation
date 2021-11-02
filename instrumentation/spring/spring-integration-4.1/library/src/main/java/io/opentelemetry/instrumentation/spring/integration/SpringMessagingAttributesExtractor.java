/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import javax.annotation.Nullable;

// this class is needed mostly for correct CONSUMER span suppression
final class SpringMessagingAttributesExtractor
    extends MessagingAttributesExtractor<MessageWithChannel, Void> {

  @Override
  public MessageOperation operation() {
    return MessageOperation.PROCESS;
  }

  @Override
  @Nullable
  protected String system(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected String destinationKind(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected String destination(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected boolean temporaryDestination(MessageWithChannel messageWithChannel) {
    return false;
  }

  @Override
  @Nullable
  protected String protocol(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected String protocolVersion(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected String url(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected String conversationId(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected Long messagePayloadSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected Long messagePayloadCompressedSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  protected String messageId(MessageWithChannel messageWithChannel, @Nullable Void unused) {
    return null;
  }
}
