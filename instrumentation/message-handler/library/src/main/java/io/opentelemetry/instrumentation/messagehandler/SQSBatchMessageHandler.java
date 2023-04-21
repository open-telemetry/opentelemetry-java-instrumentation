/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public abstract class SQSBatchMessageHandler extends BatchMessageHandler<SQSEvent.SQSMessage> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  public SQSBatchMessageHandler(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public SQSBatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation) {
    super(openTelemetry, messageOperation);
  }

  public SQSBatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation, String spanName) {
    super(openTelemetry, messageOperation, spanName);
  }

  @Override
  protected void addMessagingAttributes(SpanBuilder spanBuilder) {
    super.addMessagingAttributes(spanBuilder);
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
  }

  @Override
  public SpanContext getParentSpanContext(SQSEvent.SQSMessage message) {
    String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);

    if (parentHeader != null) {
      Context xrayContext =
          AwsXrayPropagator.getInstance()
              .extract(
                  Context.root(),
                  Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                  MapGetter.INSTANCE);

      SpanContext messageSpanCtx = Span.fromContext(xrayContext).getSpanContext();

      if (messageSpanCtx.isValid()) {
        return messageSpanCtx;
      }
    }

    return null;
  }

  private enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase(Locale.ROOT));
    }
  }
}
