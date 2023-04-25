/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public abstract class SqsBatchMessageHandler extends BatchMessageHandler<SQSEvent.SQSMessage> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  public SqsBatchMessageHandler(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public SqsBatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation) {
    super(openTelemetry, messageOperation);
  }

  public SqsBatchMessageHandler(
      OpenTelemetry openTelemetry, String messageOperation, String spanName) {
    super(openTelemetry, messageOperation, spanName);
  }

  @Override
  protected void addMessagingAttributes(SpanBuilder spanBuilder) {
    super.addMessagingAttributes(spanBuilder);
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
  }

  @Override
  public String getParentHeaderFromMessage(SQSEvent.SQSMessage message) {
    return message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
  }
}
