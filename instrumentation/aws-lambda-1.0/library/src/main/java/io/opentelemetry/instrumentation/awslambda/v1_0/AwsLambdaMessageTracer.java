/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class AwsLambdaMessageTracer extends BaseTracer {

  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  public AwsLambdaMessageTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public Context startSpan(SQSEvent event) {
    // Use event source in name if all messages have the same source, otherwise use placeholder.
    String source = "multiple_sources";
    if (!event.getRecords().isEmpty()) {
      String messageSource = event.getRecords().get(0).getEventSource();
      for (int i = 1; i < event.getRecords().size(); i++) {
        SQSMessage message = event.getRecords().get(i);
        if (!message.getEventSource().equals(messageSource)) {
          messageSource = null;
          break;
        }
      }
      if (messageSource != null) {
        source = messageSource;
      }
    }

    SpanBuilder span = tracer.spanBuilder(source + " process").setSpanKind(SpanKind.CONSUMER);

    span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    span.setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process");

    for (SQSMessage message : event.getRecords()) {
      addLinkToMessageParent(message, span);
    }

    return Context.current().with(span.startSpan());
  }

  public Context startSpan(SQSMessage message) {
    SpanBuilder span =
        tracer.spanBuilder(message.getEventSource() + " process").setSpanKind(SpanKind.CONSUMER);

    span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    span.setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process");
    span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, message.getMessageId());
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, message.getEventSource());

    addLinkToMessageParent(message, span);

    return Context.current().with(span.startSpan());
  }

  private void addLinkToMessageParent(SQSMessage message, SpanBuilder span) {
    String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
    if (parentHeader != null) {
      SpanContext parentCtx =
          Span.fromContext(ParentContextExtractor.fromXRayHeader(parentHeader)).getSpanContext();
      if (parentCtx.isValid()) {
        span.addLink(parentCtx);
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.aws-lambda-1.0";
  }
}
