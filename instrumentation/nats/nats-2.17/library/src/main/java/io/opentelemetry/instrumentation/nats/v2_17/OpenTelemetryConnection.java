/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsMessageWritableHeaders;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.instrumentation.nats.v2_17.internal.OpenTelemetryJetStream;
import io.opentelemetry.instrumentation.nats.v2_17.internal.OpenTelemetryMessageHandler;
import io.opentelemetry.instrumentation.nats.v2_17.internal.OpenTelemetrySubscription;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class OpenTelemetryConnection implements InvocationHandler {

  private final Connection delegate;
  private final Instrumenter<NatsRequest, NatsRequest> publishInstrumenter;
  private final Instrumenter<NatsRequest, NatsRequest> requestInstrumenter;
  private final Instrumenter<NatsRequest, NatsRequest> settleInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  private OpenTelemetryConnection(
      Connection connection,
      Instrumenter<NatsRequest, NatsRequest> publishInstrumenter,
      Instrumenter<NatsRequest, NatsRequest> requestInstrumenter,
      Instrumenter<NatsRequest, NatsRequest> settleInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = connection;
    this.publishInstrumenter = publishInstrumenter;
    this.requestInstrumenter = requestInstrumenter;
    this.settleInstrumenter = settleInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  static Connection wrap(
      Connection connection,
      Instrumenter<NatsRequest, NatsRequest> publishInstrumenter,
      Instrumenter<NatsRequest, NatsRequest> requestInstrumenter,
      Instrumenter<NatsRequest, NatsRequest> settleInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    return (Connection)
        Proxy.newProxyInstance(
            OpenTelemetryConnection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            new OpenTelemetryConnection(
                connection,
                publishInstrumenter,
                requestInstrumenter,
                settleInstrumenter,
                consumerProcessInstrumenter));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("publish".equals(method.getName()) && method.getReturnType().equals(Void.TYPE)) {
      publish(method, args);
      return null;
    }

    if ("request".equals(method.getName()) && method.getReturnType().equals(Message.class)) {
      return request(method, args);
    }

    if (("request".equals(method.getName()) || "requestWithTimeout".equals(method.getName()))
        && method.getReturnType().equals(CompletableFuture.class)) {
      return requestAsync(method, args);
    }

    if ("createDispatcher".equals(method.getName())
        && method.getReturnType().equals(Dispatcher.class)) {
      return createDispatcher(method, args);
    }

    if ("subscribe".equals(method.getName()) && method.getReturnType().equals(Subscription.class)) {
      return OpenTelemetrySubscription.wrap(
          (Subscription) invokeMethod(method, delegate, args), settleInstrumenter);
    }

    if ("jetStream".equals(method.getName()) && method.getReturnType().equals(JetStream.class)) {
      return OpenTelemetryJetStream.wrap(
          (JetStream) invokeMethod(method, delegate, args), settleInstrumenter);
    }

    if ("closeDispatcher".equals(method.getName())) {
      return closeDispatcher(method, args);
    }

    return invokeMethod(method, delegate, args);
  }

  private static Object invokeMethod(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  // void publish(String subject, byte[] body)
  // void publish(String subject, Headers headers, byte[] body)
  // void publish(String subject, String replyTo, byte[] body)
  // void publish(String subject, String replyTo, Headers headers, byte[] body)
  // void publish(Message message)
  private void publish(Method method, Object[] args) throws Throwable {
    String subject = null;
    String replyTo = null;
    Headers headers = null;
    byte[] body = null;

    if (method.getParameterCount() == 2
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      subject = (String) args[0];
      body = (byte[]) args[1];
    } else if (method.getParameterCount() == 3
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      subject = (String) args[0];
      headers = (Headers) args[1];
      body = (byte[]) args[2];
    } else if (method.getParameterCount() == 3
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == String.class
        && method.getParameterTypes()[2] == byte[].class) {
      subject = (String) args[0];
      replyTo = (String) args[1];
      body = (byte[]) args[2];
    } else if (method.getParameterCount() == 4
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == String.class
        && method.getParameterTypes()[2] == Headers.class
        && method.getParameterTypes()[3] == byte[].class) {
      subject = (String) args[0];
      replyTo = (String) args[1];
      headers = (Headers) args[2];
      body = (byte[]) args[3];
    } else if (method.getParameterCount() == 1
        && method.getParameterTypes()[0] == Message.class
        && args[0] != null) {
      subject = ((Message) args[0]).getSubject();
      replyTo = ((Message) args[0]).getReplyTo();
      headers = ((Message) args[0]).getHeaders();
      body = ((Message) args[0]).getData();
    }

    Context parentContext = Context.current();
    NatsRequest natsRequest = null;

    if (subject != null) {
      headers = NatsMessageWritableHeaders.create(headers);
      natsRequest = NatsRequest.create(delegate, subject, replyTo, headers, body);
    }

    Instrumenter<NatsRequest, NatsRequest> instrumenter =
        natsRequest == null
            ? publishInstrumenter
            : instrumenterFor(natsRequest, publishInstrumenter);
    if (natsRequest == null || !instrumenter.shouldStart(parentContext, natsRequest)) {
      invokeMethod(method, delegate, args);
      return;
    }

    Context context = instrumenter.start(parentContext, natsRequest);
    Throwable throwable = null;
    try (Scope ignored = context.makeCurrent()) {
      delegate.publish(subject, replyTo, headers, body);
    } catch (Throwable t) {
      throwable = t;
      throw t;
    } finally {
      instrumenter.end(context, natsRequest, null, throwable);
    }
  }

  // Message request(String subject, byte[] body, Duration timeout) throws InterruptedException;
  // Message request(String subject, Headers headers, byte[] body, Duration timeout) throws
  // InterruptedException;
  // Message request(Message message, Duration timeout) throws InterruptedException;
  @SuppressWarnings("InterruptedExceptionSwallowed")
  private Message request(Method method, Object[] args) throws Throwable {
    String subject = null;
    Headers headers = null;
    byte[] body = null;
    Duration timeout = null;

    if (method.getParameterCount() == 3
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      subject = (String) args[0];
      body = (byte[]) args[1];
      timeout = (Duration) args[2];
    } else if (method.getParameterCount() == 4
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      subject = (String) args[0];
      headers = (Headers) args[1];
      body = (byte[]) args[2];
      timeout = (Duration) args[3];
    } else if (method.getParameterCount() == 2
        && method.getParameterTypes()[0] == Message.class
        && args[0] != null) {
      subject = ((Message) args[0]).getSubject();
      headers = ((Message) args[0]).getHeaders();
      body = ((Message) args[0]).getData();
      timeout = (Duration) args[1];
    }

    Context parentContext = Context.current();
    NatsRequest natsRequest = null;

    if (subject != null) {
      headers = NatsMessageWritableHeaders.create(headers);
      natsRequest = NatsRequest.create(delegate, subject, null, headers, body);
    }

    Instrumenter<NatsRequest, NatsRequest> instrumenter =
        natsRequest == null
            ? requestInstrumenter
            : instrumenterFor(natsRequest, requestInstrumenter);
    if (timeout == null
        || natsRequest == null
        || !instrumenter.shouldStart(parentContext, natsRequest)) {
      return (Message) invokeMethod(method, delegate, args);
    }

    Context context = instrumenter.start(parentContext, natsRequest);
    NatsRequest response = null;
    Throwable throwable = null;

    try (Scope ignored = context.makeCurrent()) {
      Message result = delegate.request(subject, headers, body, timeout);

      if (result != null) {
        response = NatsRequest.create(delegate, result);
      }

      return result;
    } catch (Throwable t) {
      throwable = t;
      throw t;
    } finally {
      instrumenter.end(context, natsRequest, response, throwable);
    }
  }

  // CompletableFuture<Message> request(String subject, byte[] body);
  // CompletableFuture<Message> requestWithTimeout(String subject, byte[] body, Duration timeout);
  // CompletableFuture<Message> request(String subject, Headers headers, byte[] body);
  // CompletableFuture<Message> requestWithTimeout(String subject, Headers headers, byte[] body,
  // Duration timeout);
  // CompletableFuture<Message> request(Message message);
  // CompletableFuture<Message> requestWithTimeout(Message message, Duration timeout);
  @SuppressWarnings("unchecked")
  private CompletableFuture<Message> requestAsync(Method method, Object[] args) throws Throwable {
    String subject = null;
    Headers headers = null;
    byte[] body = null;
    Duration timeout = null;

    if ((method.getParameterCount() == 2)
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      subject = (String) args[0];
      body = (byte[]) args[1];
    } else if ((method.getParameterCount() == 3)
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      subject = (String) args[0];
      body = (byte[]) args[1];
      timeout = (Duration) args[2];
    } else if ((method.getParameterCount() == 3)
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      subject = (String) args[0];
      headers = (Headers) args[1];
      body = (byte[]) args[2];
    } else if ((method.getParameterCount() == 4)
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      subject = (String) args[0];
      headers = (Headers) args[1];
      body = (byte[]) args[2];
      timeout = (Duration) args[3];
    } else if ((method.getParameterCount() == 1)
        && method.getParameterTypes()[0] == Message.class
        && args[0] != null) {
      subject = ((Message) args[0]).getSubject();
      headers = ((Message) args[0]).getHeaders();
      body = ((Message) args[0]).getData();
    } else if ((method.getParameterCount() == 2)
        && method.getParameterTypes()[0] == Message.class
        && args[0] != null) {
      subject = ((Message) args[0]).getSubject();
      headers = ((Message) args[0]).getHeaders();
      body = ((Message) args[0]).getData();
      timeout = (Duration) args[1];
    }

    Context parentContext = Context.current();
    NatsRequest natsRequest = null;

    if (subject != null) {
      headers = NatsMessageWritableHeaders.create(headers);
      natsRequest = NatsRequest.create(delegate, subject, null, headers, body);
    }

    Instrumenter<NatsRequest, NatsRequest> instrumenter =
        natsRequest == null
            ? requestInstrumenter
            : instrumenterFor(natsRequest, requestInstrumenter);
    if (natsRequest == null || !instrumenter.shouldStart(parentContext, natsRequest)) {
      return (CompletableFuture<Message>) invokeMethod(method, delegate, args);
    }

    NatsRequest notNullNatsRequest = natsRequest;
    Context context = instrumenter.start(parentContext, notNullNatsRequest);

    CompletableFuture<Message> future;
    try {
      if (timeout != null) {
        future = delegate.requestWithTimeout(subject, headers, body, timeout);
      } else {
        future = delegate.request(subject, headers, body);
      }
    } catch (Throwable t) {
      instrumenter.end(context, notNullNatsRequest, null, t);
      throw t;
    }

    return future.whenComplete(
        (result, exception) -> {
          if (result != null) {
            NatsRequest response = NatsRequest.create(delegate, result);
            instrumenter.end(context, notNullNatsRequest, response, exception);
          } else {
            instrumenter.end(context, notNullNatsRequest, null, exception);
          }
        });
  }

  private Instrumenter<NatsRequest, NatsRequest> instrumenterFor(
      NatsRequest request, Instrumenter<NatsRequest, NatsRequest> defaultInstrumenter) {
    return request.isJetStreamSettlement() ? settleInstrumenter : defaultInstrumenter;
  }

  // public Dispatcher createDispatcher()
  // public Dispatcher createDispatcher(MessageHandler messageHandler)
  private Dispatcher createDispatcher(Method method, Object[] args) throws Throwable {
    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == MessageHandler.class) {
      args[0] =
          new OpenTelemetryMessageHandler(
              (MessageHandler) args[0], settleInstrumenter, consumerProcessInstrumenter);
    }

    Dispatcher wrapped = (Dispatcher) invokeMethod(method, delegate, args);
    return OpenTelemetryDispatcher.wrap(wrapped, settleInstrumenter, consumerProcessInstrumenter);
  }

  // public void closeDispatcher(Dispatcher dispatcher)
  private Object closeDispatcher(Method method, Object[] args) throws Throwable {
    if (method.getParameterCount() == 1
        && args[0] instanceof Proxy
        && Proxy.getInvocationHandler(args[0]) instanceof OpenTelemetryDispatcher) {
      args[0] = ((OpenTelemetryDispatcher) Proxy.getInvocationHandler(args[0])).getDelegate();
    }

    return invokeMethod(method, delegate, args);
  }
}
