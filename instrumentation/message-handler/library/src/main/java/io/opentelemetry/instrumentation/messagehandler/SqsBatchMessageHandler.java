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
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SqsBatchMessageHandler extends BatchMessageHandler<SQSEvent.SQSMessage> {
  private static final Logger logger = Logger.getLogger(BatchMessageHandler.class.getName());

  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

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
  public SpanContext getParentSpanContext(SQSEvent.SQSMessage message) {
    String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);

    if (parentHeader == null) {
      return null;
    }

    SpanContext spanContext = getParentSpanContextW3C(parentHeader);

    if (spanContext != null) {
      return spanContext;
    }

    spanContext = getParentSpanContextXRay(parentHeader);

    if (spanContext != null) {
      return spanContext;
    }

    logger.log(Level.WARNING, "Invalid upstream span context: {0}", parentHeader);
    return null;
  }

  private static SpanContext getParentSpanContextW3C(String parentHeader) {
    try {
      Context w3cContext =
          W3CTraceContextPropagator.getInstance()
              .extract(
                  Context.root(),
                  Collections.singletonMap("traceparent", parentHeader),
                  MapGetter.INSTANCE);

      SpanContext messageSpanCtx = Span.fromContext(w3cContext).getSpanContext();

      if (messageSpanCtx.isValid()) {
        return messageSpanCtx;
      } else {
        return null;
      }
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static SpanContext getParentSpanContextXRay(String parentHeader) {
    try {
      Context xrayContext =
          AwsXrayPropagator.getInstance()
              .extract(
                  Context.root(),
                  Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                  MapGetter.INSTANCE);

      SpanContext messageSpanCtx = Span.fromContext(xrayContext).getSpanContext();

      if (messageSpanCtx.isValid()) {
        return messageSpanCtx;
      } else {
        return null;
      }
    } catch (RuntimeException e) {
      return null;
    }
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
