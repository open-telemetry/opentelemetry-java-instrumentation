/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.currentBatchSendState;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

public final class RocketMqBatchSendHelper {

  private static final VirtualField<Message, BatchSendState> BATCH_SEND_STATE =
      VirtualField.find(Message.class, BatchSendState.class);
  private static final VirtualField<SendMessageContext, BatchSendState> HOOK_SEND_STATE =
      VirtualField.find(SendMessageContext.class, BatchSendState.class);

  private final Instrumenter<SendMessageContext, Void> singleSendInstrumenter;
  private final Instrumenter<SendMessageContext, Void> sendInstrumenter;
  private final Instrumenter<SendMessageContext, Void> createInstrumenter;
  private final TextMapPropagator propagator;
  private final MessageExtractAdapter getter = new MessageExtractAdapter();

  public RocketMqBatchSendHelper(
      OpenTelemetry openTelemetry,
      IncludeExclude headers,
      boolean captureExperimentalSpanAttributes,
      boolean messageCreationSpansEnabled) {
    singleSendInstrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            openTelemetry, headers, captureExperimentalSpanAttributes);
    sendInstrumenter =
        RocketMqInstrumenterFactory.createBatchProducerInstrumenter(
            openTelemetry, headers, captureExperimentalSpanAttributes);
    createInstrumenter =
        RocketMqInstrumenterFactory.createMessageCreateInstrumenter(
            openTelemetry, headers, messageCreationSpansEnabled);
    propagator = openTelemetry.getPropagators().getTextMapPropagator();
  }

  public BatchSendState createBatchSendState(Object producer, boolean callbackCompletionExpected) {
    return new BatchSendState(
        Context.current(),
        RocketMqNamespaceUtil.getNamespace(producer),
        callbackCompletionExpected);
  }

  public BatchSendState startSend(
      Object producer, Message message, CommunicationMode mode, boolean hasCallback) {
    BatchSendState state = BATCH_SEND_STATE.get(message);
    if (state != null) {
      return state;
    }
    state = createBatchSendState(producer, hasCallback);
    state.request = new MessageCreateContext(message, state.namespace);
    state.request.setCommunicationMode(mode);
    state.operationInstrumenter = singleSendInstrumenter;
    if (singleSendInstrumenter.shouldStart(state.parentContext, state.request)) {
      state.sendContext = singleSendInstrumenter.start(state.parentContext, state.request);
    }
    BATCH_SEND_STATE.set(message, state);
    return state;
  }

  public void completeSend(BatchSendState state, @Nullable Throwable error) {
    if (state.operationInstrumenter == singleSendInstrumenter) {
      if (state.request != null && BATCH_SEND_STATE.get(state.request.getMessage()) == state) {
        BATCH_SEND_STATE.set(state.request.getMessage(), null);
      }
      completeBatchSend(state, error);
    }
  }

  public void beforeBatchEncode(Message batch) {
    BatchSendState state = currentBatchSendState().get();
    if (state == null || state.request != null) {
      return;
    }
    state.prepare(batch);
  }

  public void completeBatchSend(BatchSendState state, @Nullable Throwable error) {
    if (error != null || (!state.callbackCompletionExpected && !state.wasClaimedAsynchronously())) {
      state.end(error);
    }
  }

  @Nullable
  public SendCallback wrap(@Nullable SendCallback delegate, BatchSendState state) {
    if (delegate == null) {
      return delegate;
    }
    return new SendCallback() {
      @Override
      public void onSuccess(SendResult sendResult) {
        state.end(null);
        try (Scope ignored = state.parentContext.makeCurrent()) {
          delegate.onSuccess(sendResult);
        }
      }

      @Override
      public void onException(Throwable e) {
        state.end(e);
        try (Scope ignored = state.parentContext.makeCurrent()) {
          delegate.onException(e);
        }
      }
    };
  }

  public SendMessageHook createSendMessageHook() {
    SendMessageHook delegate = new TracingSendMessageHookImpl(singleSendInstrumenter);
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
        if (state.hookContext != null) {
          HOOK_SEND_STATE.set(state.hookContext, null);
        }
        HOOK_SEND_STATE.set(context, state);
        state.hookContext = context;
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
        if ((context.getSendResult() != null || context.getException() != null)
            && CommunicationMode.ASYNC == context.getCommunicationMode()) {
          state.end(context.getException());
        }
      }
    };
  }

  @Nullable
  private static BatchSendState getState(@Nullable SendMessageContext context) {
    if (context == null || context.getMessage() == null) {
      return null;
    }
    BatchSendState state = HOOK_SEND_STATE.get(context);
    return state == null ? BATCH_SEND_STATE.get(context.getMessage()) : state;
  }

  public final class BatchSendState {
    private final Context parentContext;
    @Nullable private final String namespace;
    private final boolean callbackCompletionExpected;
    private final AtomicBoolean ended = new AtomicBoolean();

    @Nullable private SendMessageContext request;
    @Nullable private Context sendContext;
    @Nullable private SendMessageContext hookContext;
    private Instrumenter<SendMessageContext, Void> operationInstrumenter = sendInstrumenter;

    private BatchSendState(
        Context parentContext, @Nullable String namespace, boolean callbackCompletionExpected) {
      this.parentContext = parentContext;
      this.namespace = namespace;
      this.callbackCompletionExpected = callbackCompletionExpected;
    }

    @Nullable
    public Context getSendContext() {
      return sendContext;
    }

    private void prepare(Message batch) {
      request = new BatchSendContext(batch, namespace);
      List<Context> creationContexts = new ArrayList<>();
      List<Message> messagesWithoutCreationContext = new ArrayList<>();
      List<Context> extractedContextsWithoutCreationContext = new ArrayList<>();
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
          extractedContextsWithoutCreationContext.add(creationContext);
        }
        creationContexts.add(creationContext);
      }

      RocketMqBatchSendSpanLinksExtractor.setContexts(request, creationContexts);
      if (sendInstrumenter.shouldStart(parentContext, request)) {
        sendContext = sendInstrumenter.start(parentContext, request);
      }
      Context fallbackContext = sendContext == null ? parentContext : sendContext;
      for (int i = 0; i < messagesWithoutCreationContext.size(); i++) {
        propagator.inject(
            extractedContextsWithoutCreationContext.get(i).with(Span.fromContext(fallbackContext)),
            messagesWithoutCreationContext.get(i),
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

    private boolean wasClaimedAsynchronously() {
      return request != null && CommunicationMode.ASYNC == request.getCommunicationMode();
    }

    private void end(@Nullable Throwable error) {
      if (!ended.compareAndSet(false, true)) {
        return;
      }
      if (sendContext != null && request != null) {
        operationInstrumenter.end(sendContext, request, null, error);
      }
      if (request != null) {
        RocketMqBatchSendSpanLinksExtractor.clearContexts(request);
        if (BATCH_SEND_STATE.get(request.getMessage()) == this) {
          BATCH_SEND_STATE.set(request.getMessage(), null);
        }
      }
      if (hookContext != null) {
        HOOK_SEND_STATE.set(hookContext, null);
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

  private enum MessagePropertySetter implements TextMapSetter<Message> {
    INSTANCE;

    @Override
    public void set(@Nullable Message carrier, String key, String value) {
      if (carrier != null) {
        carrier.getProperties().put(key, value);
      }
    }
  }
}
