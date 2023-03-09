/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

// this class is needed mostly for correct CONSUMER span suppression
enum SpringMessagingAttributesGetter
    implements MessagingAttributesGetter<MessageWithChannel, Void> {
  INSTANCE;

  @Override
  @Nullable
  public String getSystem(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String getDestinationKind(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String getDestination(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(MessageWithChannel messageWithChannel) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(MessageWithChannel messageWithChannel, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(MessageWithChannel request, String name) {
    Object value = request.getMessage().getHeaders().get(name);
    if (value != null) {
      return Collections.singletonList(value.toString());
    }
    return Collections.emptyList();
  }
}
