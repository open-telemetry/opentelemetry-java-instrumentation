/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.impl.Headers;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

class NatsRequestMessagingAttributesGetterFactory {

  static <RESPONSE> MessagingAttributesGetter<NatsRequest, RESPONSE> create() {
    return new MessagingAttributesGetter<NatsRequest, RESPONSE>() {
      @Nullable
      @Override
      public String getSystem(NatsRequest request) {
        return "nats";
      }

      @Nullable
      @Override
      public String getDestination(NatsRequest request) {
        return request.getSubject();
      }

      @Nullable
      @Override
      public String getDestinationTemplate(NatsRequest request) {
        return null;
      }

      @Override
      public boolean isTemporaryDestination(NatsRequest request) {
        return false;
      }

      @Override
      public boolean isAnonymousDestination(NatsRequest request) {
        return false;
      }

      @Nullable
      @Override
      public String getConversationId(NatsRequest request) {
        return null;
      }

      @Nullable
      @Override
      public Long getMessageBodySize(NatsRequest request) {
        return request.getDataSize();
      }

      @Nullable
      @Override
      public Long getMessageEnvelopeSize(NatsRequest request) {
        return null;
      }

      @Nullable
      @Override
      public String getMessageId(NatsRequest request, @Nullable RESPONSE unused) {
        return null;
      }

      @Nullable
      @Override
      public String getClientId(NatsRequest request) {
        return String.valueOf(request.getClientId());
      }

      @Nullable
      @Override
      public Long getBatchMessageCount(NatsRequest request, @Nullable RESPONSE unused) {
        return null;
      }

      @Override
      public List<String> getMessageHeader(NatsRequest request, String name) {
        Headers headers = request.getHeaders();
        return headers == null || headers.get(name) == null
            ? Collections.emptyList()
            : headers.get(name);
      }
    };
  }

  private NatsRequestMessagingAttributesGetterFactory() {}
}
