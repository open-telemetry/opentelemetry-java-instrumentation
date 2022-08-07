/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;

// this class is needed mostly for correct CONSUMER span suppression
enum SpringMessagingAttributesGetter
    implements MessagingAttributesGetter<MessageWithChannel, Void> {
  INSTANCE;

  @Override
  @Nullable
  public String system(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String destinationKind(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String destination(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  public boolean temporaryDestination(MessageWithChannel messageWithChannel) {
    return false;
  }

  @Override
  @Nullable
  public String protocol(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String protocolVersion(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String url(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String conversationId(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadCompressedSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String messageId(MessageWithChannel messageWithChannel, @Nullable Void unused) {
    return null;
  }
}
