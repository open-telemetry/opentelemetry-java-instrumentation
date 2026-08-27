/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class NatsSettlementOperationNameExtractor
    implements AttributesExtractor<NatsRequest, NatsRequest> {
  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, NatsRequest request) {
    String operationName = request.getJetStreamSettlementOperationName();
    if (operationName != null) {
      attributes.put(MESSAGING_OPERATION_NAME, operationName);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      NatsRequest request,
      @Nullable NatsRequest response,
      @Nullable Throwable error) {}
}
