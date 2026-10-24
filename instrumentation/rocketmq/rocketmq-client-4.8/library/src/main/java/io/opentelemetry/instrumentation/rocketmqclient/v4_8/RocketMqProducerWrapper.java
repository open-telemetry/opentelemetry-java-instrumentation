/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;

final class RocketMqProducerWrapper implements InvocationHandler {

  private static final ContextKey<SendState> SEND_STATE =
      ContextKey.named("opentelemetry-rocketmq-send");

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
      "deprecation") // Hook registration requires the deprecated implementation accessor.
  static MQProducer wrap(
      DefaultMQProducer producer,
      Instrumenter<SendMessageContext, Void> instrumenter,
      TextMapPropagator propagator) {
    wrapAsyncSenderExecutor(producer.getDefaultMQProducerImpl());
    producer
        .getDefaultMQProducerImpl()
        .registerSendMessageHook(createSendMessageHook(instrumenter));
    return (MQProducer)
        Proxy.newProxyInstance(
            MQProducer.class.getClassLoader(),
            new Class<?>[] {MQProducer.class},
            new RocketMqProducerWrapper(producer, instrumenter, propagator));
  }

  private static void wrapAsyncSenderExecutor(DefaultMQProducerImpl implementation) {
    // Older clients prepare asynchronous sends on the calling thread.
    try {
      Method getter = DefaultMQProducerImpl.class.getMethod("getAsyncSenderExecutor");
      Method setter =
          DefaultMQProducerImpl.class.getMethod("setAsyncSenderExecutor", ExecutorService.class);
      ExecutorService executor = (ExecutorService) getter.invoke(implementation);
      if (executor != null) {
        setter.invoke(implementation, Context.taskWrapping(executor));
      }
    } catch (NoSuchMethodException ignored) {
      // The async sender executor was added after 4.0.
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot wrap RocketMQ async sender executor", e);
    }
  }

  static SendMessageHook createSendMessageHook(
      Instrumenter<SendMessageContext, Void> instrumenter) {
    return new TracingSendMessageHookImpl(instrumenter);
  }

  @Override
  @Nullable
  public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
    if ((method.getName().equals("send") || method.getName().equals("sendOneway"))
        && args != null
        && args.length > 0
        && args[0] instanceof Message) {
      return invokeSingle(method, args);
    }
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

    Message envelope = new Message();
    envelope.setTopic(originals.get(0).getTopic());
    envelope.setWaitStoreMsgOK(originals.get(0).isWaitStoreMsgOK());
    BatchSendContext request =
        new BatchSendContext(
            envelope, RocketMqNamespaceUtil.getNamespace(producer), originals.size());
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
    SendState state = new SendState(instrumenter, request, parentContext, context, messages);
    try (Scope ignored = context == null ? null : context.makeCurrent()) {
      for (Message original : originals) {
        SendMessage message = new SendMessage(original);
        message.state = state;
        state.messages.add(message);
        messages.add(message);
        if (context != null) {
          propagator.inject(context, message, RocketMqProducerWrapper::setProperty);
        }
      }
      Object[] delegateArgs = args.clone();
      delegateArgs[0] = messages;
      if (callbackIndex != -1) {
        delegateArgs[callbackIndex] = state.wrap((SendCallback) args[callbackIndex]);
      }
      Object result = invokeDelegate(method, delegateArgs);
      if (callbackIndex == -1) {
        request.setSendResult((SendResult) result);
        state.end(null);
      }
      return result;
    } catch (Throwable t) {
      state.end(t);
      throw t;
    }
  }

  @Nullable
  private Object invokeSingle(Method method, Object[] args) throws Throwable {
    Message message = (Message) args[0];
    if (message.getProperties() == null) {
      MessageAccessor.setProperties(message, new HashMap<>());
    }
    SendMessageContext request =
        new SingleSendContext(message, RocketMqNamespaceUtil.getNamespace(producer));
    int callbackIndex = -1;
    for (int i = 1; i < method.getParameterCount(); i++) {
      if (method.getParameterTypes()[i] == SendCallback.class) {
        callbackIndex = i;
        break;
      }
    }
    request.setCommunicationMode(
        method.getName().equals("sendOneway")
            ? CommunicationMode.ONEWAY
            : callbackIndex == -1 ? CommunicationMode.SYNC : CommunicationMode.ASYNC);
    Context parentContext = Context.current();
    Context context =
        instrumenter.shouldStart(parentContext, request)
            ? instrumenter.start(parentContext, request)
            : null;
    SendState state = new SendState(instrumenter, request, parentContext, context, emptyList());
    Object[] delegateArgs = args.clone();
    if (callbackIndex != -1) {
      delegateArgs[callbackIndex] = state.wrap((SendCallback) args[callbackIndex]);
    }
    Context operationContext = (context == null ? parentContext : context).with(SEND_STATE, state);
    try (Scope ignored = operationContext.makeCurrent()) {
      Object result = invokeDelegate(method, delegateArgs);
      if (callbackIndex == -1) {
        request.setSendResult((SendResult) result);
        state.end(null);
      }
      return result;
    } catch (Throwable t) {
      state.end(request.getException() == null ? t : request.getException());
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
    SendState current = Context.current().get(SEND_STATE);
    if (current != null && current.request.getMessage() == message) {
      return current;
    }
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

  static class SingleSendContext extends SendMessageContext {
    @Nullable private final String namespace;

    private SingleSendContext(Message message, @Nullable String namespace) {
      setMessage(message);
      this.namespace = namespace;
    }

    @Nullable
    @Override
    public String getNamespace() {
      return namespace;
    }
  }

  static final class BatchSendContext extends SingleSendContext {
    final long messageCount;

    private BatchSendContext(Message message, @Nullable String namespace, long messageCount) {
      super(message, namespace);
      this.messageCount = messageCount;
    }
  }

  static final class SendState {
    private final Instrumenter<SendMessageContext, Void> instrumenter;
    private final SendMessageContext request;
    private final Context parentContext;
    @Nullable private final Context context;
    private final List<SendMessage> messages;
    private final AtomicBoolean ended = new AtomicBoolean();

    private SendState(
        Instrumenter<SendMessageContext, Void> instrumenter,
        SendMessageContext request,
        Context parentContext,
        @Nullable Context context,
        List<SendMessage> messages) {
      this.instrumenter = instrumenter;
      this.request = request;
      this.parentContext = parentContext;
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
            try (Scope ignored = parentContext.makeCurrent()) {
              delegate.onSuccess(sendResult);
            }
          }
        }

        @Override
        public void onException(Throwable e) {
          end(e);
          if (delegate != null) {
            try (Scope ignored = parentContext.makeCurrent()) {
              delegate.onException(e);
            }
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
