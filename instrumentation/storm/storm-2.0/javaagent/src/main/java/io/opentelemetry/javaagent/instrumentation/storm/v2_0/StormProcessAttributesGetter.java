/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;
import org.apache.storm.tuple.TupleImpl;

final class StormProcessAttributesGetter implements MessagingAttributesGetter<TupleImpl, Void> {

  @Override
  public String getSystem(TupleImpl tuple) {
    return "storm";
  }

  @Nullable
  @Override
  public String getDestination(TupleImpl tuple) {
    return tuple.getSourceStreamId();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(TupleImpl tuple) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(TupleImpl tuple) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(TupleImpl tuple) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(TupleImpl tuple) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(TupleImpl tuple) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(TupleImpl tuple) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(TupleImpl tuple, @Nullable Void unused) {
    Object messageId = tuple.getMessageId();
    return messageId == null ? null : messageId.toString();
  }

  @Nullable
  @Override
  public String getClientId(TupleImpl tuple) {
    return tuple.getSourceComponent();
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(TupleImpl tuple, @Nullable Void unused) {
    return null;
  }
}
