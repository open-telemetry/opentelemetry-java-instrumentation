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
import javax.annotation.Nullable;
import java.util.Collection;

public abstract class BatchMessageHandler<T> {
  protected String messagingOperation;
  protected OpenTelemetry openTelemetry;
  protected SpanNameExtractor<Collection<T>> spanNameExtractor;

  protected Instrumenter<Collection<T>, Void> messageInstrumenter;

  public BatchMessageHandler(OpenTelemetry openTelemetry) {
    this(openTelemetry, MessageOperation.RECEIVE.name());
  }

  public BatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation) {
    this(openTelemetry, messageOperation, messages -> "Batch Message");
  }

  public BatchMessageHandler(
      OpenTelemetry openTelemetry, String messageOperation, SpanNameExtractor<Collection<T>> spanNameExtractor) {
    this.openTelemetry = openTelemetry;
    this.spanNameExtractor = spanNameExtractor;
    this.messagingOperation = messageOperation;

    setup();
  }

  protected abstract void setup();

  protected abstract void doHandleMessages(Collection<T> messages);

  protected AttributesExtractor<Collection<T>, Void> getGenericAttributesExtractor() {
    return
        new AttributesExtractor<Collection<T>, Void>() {

          @Override
          public void onStart(
              AttributesBuilder attributes, Context parentContext, Collection<T> messages) {
            attributes.put(SemanticAttributes.MESSAGING_OPERATION, messagingOperation);
          }

          @Override
          public void onEnd(
              AttributesBuilder attributes,
              Context context,
              Collection<T> messages,
              @Nullable Void unused,
              @Nullable Throwable error) {

          }
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
