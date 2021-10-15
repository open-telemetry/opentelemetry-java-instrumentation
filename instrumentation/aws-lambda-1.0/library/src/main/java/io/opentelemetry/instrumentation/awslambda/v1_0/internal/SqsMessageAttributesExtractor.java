/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

class SqsMessageAttributesExtractor implements AttributesExtractor<SQSMessage, Void> {
  @Override
  public void onStart(AttributesBuilder attributes, SQSMessage message) {
    attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    attributes.put(SemanticAttributes.MESSAGING_OPERATION, "process");
    attributes.put(SemanticAttributes.MESSAGING_MESSAGE_ID, message.getMessageId());
    attributes.put(SemanticAttributes.MESSAGING_DESTINATION, message.getEventSource());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      SQSMessage message,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
