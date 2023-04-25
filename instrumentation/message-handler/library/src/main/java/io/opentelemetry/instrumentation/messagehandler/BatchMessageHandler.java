/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BatchMessageHandler<T> {
  private static final Logger logger = Logger.getLogger(BatchMessageHandler.class.getName());
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  protected String messagingOperation;
  protected OpenTelemetry openTelemetry;
  protected String spanName;

  public BatchMessageHandler(OpenTelemetry openTelemetry) {
    this(openTelemetry, MessageOperation.RECEIVE.name());
  }

  public BatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation) {
    this(openTelemetry, messageOperation, "Batch Message");
  }

  public BatchMessageHandler(
      OpenTelemetry openTelemetry, String messageOperation, String spanName) {
    this.openTelemetry = openTelemetry;
    this.spanName = spanName;
    this.messagingOperation = messageOperation;
  }

  public abstract String getParentHeaderFromMessage(T t);

  protected abstract void doHandleMessages(Collection<T> messages);

  protected void addMessagingAttributes(SpanBuilder spanBuilder) {
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_OPERATION, messagingOperation);
  }

  public void handleMessages(Collection<T> messages) {
    TracerBuilder tracerBuilder =
        openTelemetry
            .tracerBuilder("io.opentelemetry.message.handler")
            .setInstrumentationVersion("1.0");

    Span parentSpan = Span.current();

    SpanBuilder spanBuilder =
        tracerBuilder
            .build()
            .spanBuilder(spanName)
            .setParent(Context.current().with(parentSpan))
            .setSpanKind(SpanKind.INTERNAL);

    addMessagingAttributes(spanBuilder);

    for (T t : messages) {
      SpanContext spanContext = getParentSpanContextFromHeader(getParentHeaderFromMessage(t));

      if (spanContext != null) {
        spanBuilder.addLink(spanContext);
      }
    }

    Span span = spanBuilder.startSpan();

    try (Scope scope = span.makeCurrent()) {
      doHandleMessages(messages);
    } finally {
      span.end();
    }
  }

  public SpanContext getParentSpanContextFromHeader(String parentHeader) {
    if (parentHeader == null) {
      return null;
    }

    // We do not know if the upstream is W3C or X-Ray format.
    // We will first try to decode it as a X-Ray trace context.
    // Then we will try to decode it as a W3C trace context.
    SpanContext spanContext = getParentSpanContextXRay(parentHeader);

    if (spanContext != null) {
      return spanContext;
    }

    spanContext = getParentSpanContextW3C(parentHeader);

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
