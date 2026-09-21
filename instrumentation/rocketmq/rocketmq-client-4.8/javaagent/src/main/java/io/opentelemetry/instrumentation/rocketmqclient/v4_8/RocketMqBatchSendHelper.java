/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.currentBatchSendState;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
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

  private final Instrumenter<SendMessageContext, Void> sendInstrumenter;
  private final Instrumenter<SendMessageContext, Void> createInstrumenter;
  private final TextMapPropagator propagator;
  private final MessageExtractAdapter getter = new MessageExtractAdapter();

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
  public BatchSendState createBatchSendState(Object producer, boolean callbackCompletionExpected) {
    if (!emitStableMessagingSemconv()) {
      return null;
    }
    return new BatchSendState(
        Context.current(),
        RocketMqNamespaceUtil.getNamespace(producer),
        callbackCompletionExpected);
  }

  public void beforeBatchEncode(Message batch) {
    BatchSendState state = currentBatchSendState().get();
    if (state == null || state.request != null) {
      return;
    }
    state.prepare(batch);
  }

  public void completeBatchSend(@Nullable BatchSendState state, @Nullable Throwable error) {
    if (state == null) {
      return;
    }
    if (error != null || (!state.callbackCompletionExpected && !state.wasClaimedAsynchronously())) {
      state.end(error);
    }
  }

  @Nullable
  public SendCallback wrap(@Nullable SendCallback delegate, @Nullable BatchSendState state) {
    if (delegate == null || state == null) {
      return delegate;
    }
    return new SendCallback() {
      @Override
      public void onSuccess(SendResult sendResult) {
        state.end(null);
        delegate.onSuccess(sendResult);
      }

      @Override
      public void onException(Throwable e) {
        state.end(e);
        delegate.onException(e);
      }
    };
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
    return context == null || context.getMessage() == null
        ? null
        : BATCH_SEND_STATE.get(context.getMessage());
  }

  public final class BatchSendState {
    private final Context parentContext;
    @Nullable private final String namespace;
    private final boolean callbackCompletionExpected;

    @Nullable private BatchSendContext request;
    @Nullable private Context sendContext;
    private boolean ended;

    private BatchSendState(
        Context parentContext, @Nullable String namespace, boolean callbackCompletionExpected) {
      this.parentContext = parentContext;
      this.namespace = namespace;
      this.callbackCompletionExpected = callbackCompletionExpected;
    }

    private void prepare(Message batch) {
      request = new BatchSendContext(batch, namespace);
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

    private boolean wasClaimedAsynchronously() {
      return request != null && CommunicationMode.ASYNC == request.getCommunicationMode();
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
        BATCH_SEND_STATE.set(request.getMessage(), null);
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
