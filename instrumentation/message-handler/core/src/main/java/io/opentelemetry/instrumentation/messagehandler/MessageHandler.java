/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import javax.annotation.Nullable;

public abstract class MessageHandler<T> {
  private final MessageOperation messagingOperation;
  private final OpenTelemetry openTelemetry;
  private final SpanNameExtractor<Collection<T>> spanNameExtractor;

  protected Instrumenter<Collection<T>, Void> messageInstrumenter;

  public MessageHandler(
      OpenTelemetry openTelemetry, SpanNameExtractor<Collection<T>> spanNameExtractor) {
    this(openTelemetry, spanNameExtractor, MessageOperation.RECEIVE);
  }

  public MessageHandler(
      OpenTelemetry openTelemetry,
      SpanNameExtractor<Collection<T>> spanNameExtractor,
      MessageOperation messageOperation) {
    this.openTelemetry = openTelemetry;
    this.spanNameExtractor = spanNameExtractor;
    this.messagingOperation = messageOperation;

    setup();
  }

  protected MessageOperation getMessagingOperation() {
    return messagingOperation;
  }

  protected OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  protected SpanNameExtractor<Collection<T>> getSpanNameExtractor() {
    return spanNameExtractor;
  }

  protected abstract void setup();

  protected abstract void doHandleMessages(Collection<T> messages);

  protected AttributesExtractor<Collection<T>, Void> getGenericAttributesExtractor() {
    return new AttributesExtractor<Collection<T>, Void>() {

      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, Collection<T> messages) {
        attributes.put(SemanticAttributes.MESSAGING_OPERATION, messagingOperation.name());
      }

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          Collection<T> messages,
          @Nullable Void unused,
          @Nullable Throwable error) {}
    };
  }

  public void handleMessages(Collection<T> messages) {
    Context parentContext = Context.current();
    if (messageInstrumenter.shouldStart(parentContext, messages)) {
      io.opentelemetry.context.Context otelContext =
          messageInstrumenter.start(parentContext, messages);
      Throwable error = null;
      try (Scope ignored = otelContext.makeCurrent()) {
        doHandleMessages(messages);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        messageInstrumenter.end(otelContext, messages, null, error);
      }
    } else {
      doHandleMessages(messages);
    }
  }
}
