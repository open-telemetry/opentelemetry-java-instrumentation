/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
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
  public String getDestination(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Nullable
  @Override
  public String getDestinationTemplate(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(MessageWithChannel messageWithChannel) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(MessageWithChannel messageWithChannel) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(MessageWithChannel messageWithChannel, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(MessageWithChannel messageWithChannel) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(MessageWithChannel messageWithChannel, @Nullable Void unused) {
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
