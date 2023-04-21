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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;

public abstract class BatchMessageHandler<T> {
  protected String messagingOperation;
  protected OpenTelemetry openTelemetry;
  protected String spanName;

  public BatchMessageHandler(OpenTelemetry openTelemetry) {
    this(openTelemetry, MessageOperation.RECEIVE.name());
  }

  public BatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation) {
    this(openTelemetry, messageOperation, "Batch Message Handler");
  }

  public BatchMessageHandler(
      OpenTelemetry openTelemetry, String messageOperation, String spanName) {
    this.openTelemetry = openTelemetry;
    this.spanName = spanName;
    this.messagingOperation = messageOperation;
  }

  public abstract SpanContext getParentSpanContext(T t);

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

    for (T t : messages) {
      SpanContext spanContext = getParentSpanContext(t);

      if (spanContext != null) {
        spanBuilder.addLink(spanContext);
      }
    }

    addMessagingAttributes(spanBuilder);

    Span span = spanBuilder.startSpan();

    try (Scope scope = span.makeCurrent()) {
      doHandleMessages(messages);
    }

    span.end();
  }
}
