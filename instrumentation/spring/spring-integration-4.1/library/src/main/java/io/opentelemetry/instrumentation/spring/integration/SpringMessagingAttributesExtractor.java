/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

// this class is needed mostly for correct CONSUMER span suppression
final class SpringMessagingAttributesExtractor
    extends MessagingAttributesExtractor<MessageWithChannel, Void> {

  @Override
  public MessageOperation operation() {
    return MessageOperation.PROCESS;
  }

  @Override
  protected @Nullable String system(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable String destinationKind(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable String destination(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected boolean temporaryDestination(MessageWithChannel messageWithChannel) {
    return false;
  }

  @Override
  protected @Nullable String protocol(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable String protocolVersion(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable String url(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable String conversationId(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadCompressedSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  protected @Nullable String messageId(
      MessageWithChannel messageWithChannel, @Nullable Void unused) {
    return null;
  }
}
