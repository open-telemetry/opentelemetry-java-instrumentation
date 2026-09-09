/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.common.message.Message;

final class TracingSendMessageHookImpl implements SendMessageHook {

  private static final VirtualField<SendMessageContext, Context> CONTEXT_FIELD =
      VirtualField.find(SendMessageContext.class, Context.class);

  // MessageBatch was introduced after the oldest supported RocketMQ version.
  private static final ClassValue<Method> batchEncoders =
      new ClassValue<Method>() {
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("encode");
          } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
          }
        }
      };

  private final Instrumenter<SendMessageContext, Void> instrumenter;
  private final Instrumenter<SendMessageContext, Void> messageCreateInstrumenter;
  private final TextMapPropagator propagator;
  private final MessageExtractAdapter getter = new MessageExtractAdapter();

  TracingSendMessageHookImpl(
      Instrumenter<SendMessageContext, Void> instrumenter,
      Instrumenter<SendMessageContext, Void> messageCreateInstrumenter,
      TextMapPropagator propagator) {
    this.instrumenter = instrumenter;
    this.messageCreateInstrumenter = messageCreateInstrumenter;
    this.propagator = propagator;
  }

  @Override
  public String hookName() {
    return "OpenTelemetrySendMessageTraceHook";
  }

  @Override
  public void sendMessageBefore(SendMessageContext context) {
    if (context == null) {
      return;
    }
    Context parentContext = Context.current();
    Message batch =
        emitStableMessagingSemconv() && isMessageBatch(context.getMessage())
            ? context.getMessage()
            : null;
    List<Message> messagesWithoutCreationContext = emptyList();
    if (batch != null) {
      List<Context> creationContexts = new ArrayList<>();
      messagesWithoutCreationContext = new ArrayList<>();
      Context propagationContext = parentContext.with(Span.getInvalid());
      for (Object item : (Iterable<?>) batch) {
        Message message = (Message) item;
        Context creationContext = propagator.extract(propagationContext, message, getter);
        boolean hasCreationContext = Span.fromContext(creationContext).getSpanContext().isValid();
        if (!hasCreationContext) {
          SendMessageContext request =
              new MessageCreateContext(message, RocketMqNamespaceUtil.getNamespace(context));
          if (messageCreateInstrumenter.shouldStart(parentContext, request)) {
            Instant timestamp = Instant.now();
            Context createdContext =
                InstrumenterUtil.startAndEnd(
                    messageCreateInstrumenter,
                    parentContext,
                    request,
                    null,
                    null,
                    timestamp,
                    timestamp);
            creationContext = creationContext.with(Span.fromContext(createdContext));
            hasCreationContext = Span.fromContext(creationContext).getSpanContext().isValid();
            if (hasCreationContext) {
              propagator.inject(
                  creationContext,
                  message,
                  (carrier, key, value) -> carrier.getProperties().put(key, value));
            }
          }
        }
        if (!hasCreationContext) {
          messagesWithoutCreationContext.add(message);
        }
        creationContexts.add(creationContext);
      }
      RocketMqBatchSendSpanLinksExtractor.setContexts(context, creationContexts);
    }
    if (!instrumenter.shouldStart(parentContext, context)) {
      RocketMqBatchSendSpanLinksExtractor.clearContexts(context);
      return;
    }
    Context sendContext = instrumenter.start(parentContext, context);
    CONTEXT_FIELD.set(context, sendContext);
    if (batch != null) {
      for (Message message : messagesWithoutCreationContext) {
        propagator.inject(
            sendContext, message, (carrier, key, value) -> carrier.getProperties().put(key, value));
      }
      // DefaultMQProducer encodes batches before invoking the send hook.
      try {
        byte[] body = (byte[]) batchEncoders.get(batch.getClass()).invoke(batch);
        // The broker appends batch-level properties after per-message properties, so propagation
        // headers on the envelope would overwrite the individual creation contexts.
        propagator.fields().forEach(batch.getProperties()::remove);
        batch.setBody(body);
      } catch (ReflectiveOperationException e) {
        instrumenter.end(sendContext, context, null, e);
        CONTEXT_FIELD.set(context, null);
        RocketMqBatchSendSpanLinksExtractor.clearContexts(context);
      }
    }
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null) {
      return;
    }
    Context otelContext = CONTEXT_FIELD.get(context);
    if (otelContext != null
        && (context.getSendResult() != null
            || context.getException() != null
            || CommunicationMode.ONEWAY == context.getCommunicationMode())) {
      instrumenter.end(otelContext, context, null, context.getException());
      CONTEXT_FIELD.set(context, null);
      RocketMqBatchSendSpanLinksExtractor.clearContexts(context);
    }
  }

  private static boolean isMessageBatch(@Nullable Message message) {
    return message != null
        && message.getClass().getName().equals("org.apache.rocketmq.common.message.MessageBatch");
  }

  static final class MessageCreateContext extends SendMessageContext {
    @Nullable private final String namespace;

    MessageCreateContext(Message message, @Nullable String namespace) {
      setMessage(message);
      this.namespace = namespace;
    }

    @Nullable
    @Override
    public String getNamespace() {
      return namespace;
    }
  }
}
