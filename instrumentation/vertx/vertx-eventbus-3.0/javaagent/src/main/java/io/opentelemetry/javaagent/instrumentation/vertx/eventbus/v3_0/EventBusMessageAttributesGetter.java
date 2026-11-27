/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.vertx.core.MultiMap;
import java.util.List;
import javax.annotation.Nullable;

enum EventBusMessageAttributesGetter implements MessagingAttributesGetter<EventBusRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(EventBusRequest request) {
    return "vertx";
  }

  @Override
  public String getDestination(EventBusRequest request) {
    return request.getAddress();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(EventBusRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(EventBusRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(EventBusRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(EventBusRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(EventBusRequest request, @Nullable Void response) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(EventBusRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(EventBusRequest request, @Nullable Void response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(EventBusRequest request, String name) {
    MultiMap headers = request.headers();
    return headers != null ? headers.getAll(name) : emptyList();
  }

  @Override
  public Iterable<String> getMessageHeaderNames(EventBusRequest request) {
    MultiMap headers = request.headers();
    return headers != null ? headers.names() : emptyList();
  }
}
