/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.common.message.Message;

public final class RocketMqBatchSendHelper {

  private static final VirtualField<Message, BatchSendState> BATCH_SEND_STATE =
      VirtualField.find(Message.class, BatchSendState.class);

  private final Instrumenter<SendMessageContext, Void> sendInstrumenter;
  private final Instrumenter<SendMessageContext, Void> createInstrumenter;
  private final TextMapPropagator propagator;
  private final MessageExtractAdapter getter = new MessageExtractAdapter();
  private final ThreadLocal<BatchSendState> currentState = new ThreadLocal<>();

  public RocketMqBatchSendHelper(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes,
      boolean messageCreationSpansEnabled) {
    sendInstrumenter =
        RocketMqInstrumenterFactory.createBatchProducerInstrumenter(
            openTelemetry, headers, captureExperimentalSpanAttributes);
    createInstrumenter =
        RocketMqInstrumenterFactory.createMessageCreateInstrumenter(
            openTelemetry, headers, messageCreationSpansEnabled);
    propagator = openTelemetry.getPropagators().getTextMapPropagator();
  }

  @Nullable
  public Object batchSendStart(Object producer) {
    if (!emitStableMessagingSemconv()) {
      return null;
    }
    BatchSendState state =
        new BatchSendState(
            Context.current(), RocketMqNamespaceUtil.getNamespace(producer), currentState.get());
    currentState.set(state);
    return state;
  }

  public void beforeBatchEncode(Message batch) {
    BatchSendState state = currentState.get();
    if (state == null || state.prepared) {
      return;
    }
    state.prepare(batch);
  }

  public void batchSendEnd(@Nullable Object stateObject, @Nullable Throwable error) {
    if (stateObject == null) {
      return;
    }
    BatchSendState state = (BatchSendState) stateObject;
    if (currentState.get() == state) {
      if (state.previous == null) {
        currentState.remove();
      } else {
        currentState.set(state.previous);
      }
    }
    if (!state.claimed) {
      state.end(error);
    }
  }

  public SendMessageHook wrap(SendMessageHook delegate) {
    return new SendMessageHook() {
      @Override
      public String hookName() {
        return delegate.hookName();
      }

      @Override
      public void sendMessageBefore(SendMessageContext context) {
        BatchSendState state = getState(context);
        if (state == null) {
          delegate.sendMessageBefore(context);
          return;
        }
        state.claimed = true;
        state.copyFrom(context);
      }

      @Override
      public void sendMessageAfter(SendMessageContext context) {
        BatchSendState state = getState(context);
        if (state == null) {
          delegate.sendMessageAfter(context);
          return;
        }
        state.copyFrom(context);
        if (context.getSendResult() != null
            || context.getException() != null
            || CommunicationMode.ONEWAY == context.getCommunicationMode()) {
          state.end(context.getException());
        }
      }
    };
  }

  @Nullable
  private static BatchSendState getState(@Nullable SendMessageContext context) {
    return context == null || context.getMessage() == null
        ? null
        : BATCH_SEND_STATE.get(context.getMessage());
  }

  private final class BatchSendState {
    private final Context parentContext;
    @Nullable private final String namespace;
    @Nullable private final BatchSendState previous;

    @Nullable private BatchSendContext request;
    @Nullable private Context sendContext;
    @Nullable private Message batch;
    private boolean prepared;
    private boolean claimed;
    private boolean ended;

    private BatchSendState(
        Context parentContext,
        @Nullable String namespace,
        @Nullable BatchSendState previous) {
      this.parentContext = parentContext;
      this.namespace = namespace;
      this.previous = previous;
    }

    private void prepare(Message batch) {
      prepared = true;
      this.batch = batch;
      List<Context> creationContexts = new ArrayList<>();
      List<Message> messagesWithoutCreationContext = new ArrayList<>();
      Context extractionContext = parentContext.with(Span.getInvalid());
      for (Object item : (Iterable<?>) batch) {
        Message message = (Message) item;
        Context creationContext = propagator.extract(extractionContext, message, getter);
        if (!Span.fromContext(creationContext).getSpanContext().isValid()) {
          MessageCreateContext createRequest = new MessageCreateContext(message, namespace);
          if (createInstrumenter.shouldStart(parentContext, createRequest)) {
            Context createdContext = createInstrumenter.start(parentContext, createRequest);
            createInstrumenter.end(createdContext, createRequest, null, null);
            creationContext = creationContext.with(Span.fromContext(createdContext));
            propagator.inject(creationContext, message, MessagePropertySetter.INSTANCE);
          }
        }
        if (!Span.fromContext(creationContext).getSpanContext().isValid()) {
          messagesWithoutCreationContext.add(message);
        }
        creationContexts.add(creationContext);
      }

      request = new BatchSendContext(batch, namespace);
      RocketMqBatchSendSpanLinksExtractor.setContexts(request, creationContexts);
      if (sendInstrumenter.shouldStart(parentContext, request)) {
        sendContext = sendInstrumenter.start(parentContext, request);
      }
      Context fallbackContext = sendContext == null ? parentContext : sendContext;
      for (Message message : messagesWithoutCreationContext) {
        Context extracted = propagator.extract(extractionContext, message, getter);
        propagator.inject(
            extracted.with(Span.fromContext(fallbackContext)),
            message,
            MessagePropertySetter.INSTANCE);
      }
      BATCH_SEND_STATE.set(batch, this);
    }

    private void copyFrom(SendMessageContext context) {
      if (request == null) {
        return;
      }
      request.setBrokerAddr(context.getBrokerAddr());
      request.setCommunicationMode(context.getCommunicationMode());
      request.setException(context.getException());
      request.setSendResult(context.getSendResult());
    }

    private void end(@Nullable Throwable error) {
      if (ended) {
        return;
      }
      ended = true;
      if (sendContext != null && request != null) {
        sendInstrumenter.end(sendContext, request, null, error);
      }
      if (request != null) {
        RocketMqBatchSendSpanLinksExtractor.clearContexts(request);
      }
      if (batch != null) {
        BATCH_SEND_STATE.set(batch, null);
      }
    }
  }

  private static class MessageCreateContext extends SendMessageContext {
    @Nullable private final String namespace;

    private MessageCreateContext(Message message, @Nullable String namespace) {
      setMessage(message);
      this.namespace = namespace;
    }

    @Nullable
    @Override
    public String getNamespace() {
      return namespace;
    }
  }

  private static final class BatchSendContext extends MessageCreateContext {
    private BatchSendContext(Message message, @Nullable String namespace) {
      super(message, namespace);
    }
  }

  private enum MessagePropertySetter
      implements io.opentelemetry.context.propagation.TextMapSetter<Message> {
    INSTANCE;

    @Override
    public void set(@Nullable Message carrier, String key, String value) {
      if (carrier != null) {
        carrier.getProperties().put(key, value);
      }
    }
  }
}
