/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;

final class RocketMqProducerWrapper implements InvocationHandler {

  private final DefaultMQProducer producer;
  private final Instrumenter<SendMessageContext, Void> instrumenter;
  private final TextMapPropagator propagator;

  private RocketMqProducerWrapper(
      DefaultMQProducer producer,
      Instrumenter<SendMessageContext, Void> instrumenter,
      TextMapPropagator propagator) {
    this.producer = producer;
    this.instrumenter = instrumenter;
    this.propagator = propagator;
  }

  @SuppressWarnings(
      "deprecation") // send-hook registration is exposed by the producer implementation
  static MQProducer wrap(
      DefaultMQProducer producer,
      Instrumenter<SendMessageContext, Void> instrumenter,
      TextMapPropagator propagator) {
    producer
        .getDefaultMQProducerImpl()
        .registerSendMessageHook(createSendMessageHook(instrumenter));
    return (MQProducer)
        Proxy.newProxyInstance(
            MQProducer.class.getClassLoader(),
            new Class<?>[] {MQProducer.class},
            new RocketMqProducerWrapper(producer, instrumenter, propagator));
  }

  static SendMessageHook createSendMessageHook(
      Instrumenter<SendMessageContext, Void> instrumenter) {
    return new TracingSendMessageHookImpl(instrumenter);
  }

  @Override
  @Nullable
  public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
    if (!method.getName().equals("send")
        || method.getParameterCount() == 0
        || method.getParameterTypes()[0] != Collection.class
        || args == null
        || args[0] == null) {
      return invokeDelegate(method, args);
    }
    List<Message> originals = new ArrayList<>();
    for (Object item : (Collection<?>) args[0]) {
      if (item == null) {
        return invokeDelegate(method, args);
      }
      originals.add((Message) item);
    }
    if (originals.isEmpty()) {
      return invokeDelegate(method, args);
    }
    // Each encoded batch needs its own carriers when messages are reused before async completion.
    List<SendMessage> messages = new ArrayList<>();
    for (Message original : originals) {
      messages.add(new SendMessage(original));
    }

    Message envelope = new Message();
    envelope.setTopic(messages.get(0).getTopic());
    envelope.setWaitStoreMsgOK(messages.get(0).isWaitStoreMsgOK());
    BatchSendContext request =
        new BatchSendContext(
            envelope, RocketMqNamespaceUtil.getNamespace(producer), messages.size());
    int callbackIndex = -1;
    for (int i = 1; i < method.getParameterTypes().length; i++) {
      if (method.getParameterTypes()[i] == SendCallback.class) {
        callbackIndex = i;
        break;
      }
    }
    request.setCommunicationMode(
        callbackIndex == -1 ? CommunicationMode.SYNC : CommunicationMode.ASYNC);
    Context parentContext = Context.current();
    Context context =
        instrumenter.shouldStart(parentContext, request)
            ? instrumenter.start(parentContext, request)
            : null;
    SendState state = new SendState(instrumenter, request, context, messages);
    try {
      for (SendMessage message : messages) {
        message.state = state;
        if (context != null) {
          propagator.inject(context, message, RocketMqProducerWrapper::setProperty);
        }
      }
      Object[] delegateArgs = args.clone();
      delegateArgs[0] = messages;
      if (callbackIndex != -1) {
        delegateArgs[callbackIndex] = state.wrap((SendCallback) args[callbackIndex]);
      }
      try (Scope ignored = context == null ? null : context.makeCurrent()) {
        Object result = invokeDelegate(method, delegateArgs);
        if (callbackIndex == -1) {
          request.setSendResult((SendResult) result);
          state.end(null);
        }
        return result;
      }
    } catch (Throwable t) {
      state.end(t);
      throw t;
    }
  }

  @Nullable
  private Object invokeDelegate(Method method, @Nullable Object[] args) throws Throwable {
    try {
      return method.invoke(producer, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static void setProperty(@Nullable Message message, String key, String value) {
    if (message != null) {
      message.getProperties().put(key, value);
    }
  }

  @Nullable
  static SendState getState(SendMessageContext context) {
    Message message = context.getMessage();
    if (message instanceof Iterable<?>) {
      for (Object item : (Iterable<?>) message) {
        if (item instanceof SendMessage) {
          SendState state = ((SendMessage) item).state;
          if (state != null) {
            return state;
          }
        }
      }
    }
    return null;
  }

  private static final class SendMessage extends Message {
    private final Message original;
    @Nullable private volatile SendState state;

    private SendMessage(Message original) {
      this.original = original;
      super.setTopic(original.getTopic());
      setBody(original.getBody());
      setFlag(original.getFlag());
      Map<String, String> properties = original.getProperties();
      if (properties == null) {
        properties = new HashMap<>();
        MessageAccessor.setProperties(original, properties);
      }
      MessageAccessor.setProperties(this, properties);
    }

    @Override
    public void setTopic(String topic) {
      super.setTopic(topic);
      original.setTopic(topic);
    }
  }

  static final class BatchSendContext extends SendMessageContext {
    @Nullable private final String namespace;
    final long messageCount;

    private BatchSendContext(Message message, @Nullable String namespace, long messageCount) {
      setMessage(message);
      this.namespace = namespace;
      this.messageCount = messageCount;
    }

    @Nullable
    @Override
    public String getNamespace() {
      return namespace;
    }
  }

  static final class SendState {
    private final Instrumenter<SendMessageContext, Void> instrumenter;
    private final SendMessageContext request;
    @Nullable private final Context context;
    private final List<SendMessage> messages;
    private final AtomicBoolean ended = new AtomicBoolean();

    private SendState(
        Instrumenter<SendMessageContext, Void> instrumenter,
        SendMessageContext request,
        @Nullable Context context,
        List<SendMessage> messages) {
      this.instrumenter = instrumenter;
      this.request = request;
      this.context = context;
      this.messages = new ArrayList<>(messages);
    }

    void copyFrom(SendMessageContext context) {
      request.setBrokerAddr(context.getBrokerAddr());
      request.setCommunicationMode(context.getCommunicationMode());
      request.setSendResult(context.getSendResult());
      request.setException(context.getException());
    }

    private SendCallback wrap(@Nullable SendCallback delegate) {
      return new SendCallback() {
        @Override
        public void onSuccess(SendResult sendResult) {
          request.setSendResult(sendResult);
          end(null);
          if (delegate != null) {
            delegate.onSuccess(sendResult);
          }
        }

        @Override
        public void onException(Throwable e) {
          end(e);
          if (delegate != null) {
            delegate.onException(e);
          }
        }
      };
    }

    private void end(@Nullable Throwable error) {
      if (!ended.compareAndSet(false, true)) {
        return;
      }
      for (SendMessage message : messages) {
        message.state = null;
      }
      messages.clear();
      request.setException(error instanceof Exception ? (Exception) error : null);
      if (context != null) {
        instrumenter.end(context, request, null, error);
      }
    }
  }
}
