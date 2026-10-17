/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;

final class StormProducerAttributesGetter implements MessagingAttributesGetter<StormEmit, Void> {

  @Override
  public String getSystem(StormEmit emit) {
    return "storm";
  }

  @Nullable
  @Override
  public String getDestination(StormEmit emit) {
    return emit.getStreamId();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(StormEmit emit) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(StormEmit emit) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(StormEmit emit) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(StormEmit emit) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(StormEmit emit) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(StormEmit emit) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(StormEmit emit, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(StormEmit emit) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(StormEmit emit, @Nullable Void unused) {
    return null;
  }
}
