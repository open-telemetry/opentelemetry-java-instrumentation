/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators.MessagingSpanDecorator;
import java.util.List;
import javax.annotation.Nullable;

final class CamelMessagingAttributesGetter
    implements MessagingAttributesGetter<CamelRequest, Void> {

  @Nullable
  @Override
  public String getSystem(CamelRequest request) {
    return request.getMessagingSystem();
  }

  @Nullable
  @Override
  public String getDestination(CamelRequest request) {
    return request.getMessagingDestination();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(CamelRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(CamelRequest request) {
    return request.isMessagingDestinationTemporary();
  }

  @Override
  public boolean isAnonymousDestination(CamelRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(CamelRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(CamelRequest request, @Nullable Void unused) {
    MessagingSpanDecorator spanDecorator = (MessagingSpanDecorator) request.getSpanDecorator();
    return spanDecorator.getMessageId(request.getExchange());
  }

  @Nullable
  @Override
  public String getClientId(CamelRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(CamelRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(CamelRequest request, String name) {
    Object value = request.getExchange().getIn().getHeaders().get(name);
    return value == null ? emptyList() : singletonList(value.toString());
  }

  @Nullable
  @Override
  public String getDestinationPartitionId(CamelRequest request) {
    return request.getMessagingDestinationPartitionId();
  }

  @Nullable
  @Override
  public String getErrorType(
      CamelRequest request, @Nullable Void unused, @Nullable Throwable error) {
    if (error != null) {
      return error.getClass().getName();
    }
    return request.getExchange().isFailed() ? "_OTHER" : null;
  }
}
