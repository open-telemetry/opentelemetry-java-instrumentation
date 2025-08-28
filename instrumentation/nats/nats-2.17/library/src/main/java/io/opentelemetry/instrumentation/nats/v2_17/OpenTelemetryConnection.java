/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.impl.Headers;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

final class OpenTelemetryConnection implements InvocationHandler {

  private final Connection delegate;
  private final Instrumenter<NatsRequest, NatsRequest> producerInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryConnection(
      Connection connection,
      Instrumenter<NatsRequest, NatsRequest> producerInstrumenter,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = connection;
    this.producerInstrumenter = producerInstrumenter;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  public static Connection wrap(
      Connection connection,
      Instrumenter<NatsRequest, NatsRequest> producerInstrumenter,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    return (Connection)
        Proxy.newProxyInstance(
            OpenTelemetryConnection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            new OpenTelemetryConnection(
                connection,
                producerInstrumenter,
                consumerReceiveInstrumenter,
                consumerProcessInstrumenter));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("publish".equals(method.getName()) && method.getReturnType().equals(Void.TYPE)) {
      return publish(method, args);
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

    if ("closeDispatcher".equals(method.getName())) {
      return closeDispatcher(method, args);
    }

    return invokeMethod(method, delegate, args);
  }

  private static Object invokeMethod(Method method, Object target, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  // void publish(String subject, byte[] body)
  // void publish(String subject, Headers headers, byte[] body)
  // void publish(String subject, String replyTo, byte[] body)
  // void publish(String subject, String replyTo, Headers headers, byte[] body)
  // void publish(Message message)
  private Object publish(Method method, Object[] args) throws Throwable {
    NatsRequest natsRequest = null;

    if (method.getParameterCount() == 2
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      natsRequest = NatsRequest.create(delegate, null, (String) args[0], null, (byte[]) args[1]);
    } else if (method.getParameterCount() == 3
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      natsRequest =
          NatsRequest.create(delegate, null, (String) args[0], (Headers) args[1], (byte[]) args[2]);
    } else if (method.getParameterCount() == 3
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == String.class
        && method.getParameterTypes()[2] == byte[].class) {
      natsRequest =
          NatsRequest.create(delegate, (String) args[1], (String) args[0], null, (byte[]) args[2]);
    } else if (method.getParameterCount() == 4
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == String.class
        && method.getParameterTypes()[2] == Headers.class
        && method.getParameterTypes()[3] == byte[].class) {
      natsRequest =
          NatsRequest.create(
              delegate, (String) args[1], (String) args[0], (Headers) args[2], (byte[]) args[3]);
    } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Message.class) {
      natsRequest = NatsRequest.create(delegate, (Message) args[0]);
    }

    Context parentContext = Context.current();

    if (natsRequest == null || !producerInstrumenter.shouldStart(parentContext, natsRequest)) {
      return invokeMethod(method, delegate, args);
    }

    Context context = producerInstrumenter.start(parentContext, natsRequest);
    try (Scope ignored = context.makeCurrent()) {
      return invokeMethod(method, delegate, args);
    } finally {
      producerInstrumenter.end(context, natsRequest, null, null);
    }
  }

  // Message request(String subject, byte[] body, Duration timeout) throws InterruptedException;
  // Message request(String subject, Headers headers, byte[] body, Duration timeout) throws
  // InterruptedException;
  // Message request(Message message, Duration timeout) throws InterruptedException;
  private Message request(Method method, Object[] args) throws Throwable {
    NatsRequest natsRequest = null;

    if (method.getParameterCount() == 3
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      natsRequest = NatsRequest.create(delegate, null, (String) args[0], null, (byte[]) args[1]);
    } else if (method.getParameterCount() == 4
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      natsRequest =
          NatsRequest.create(delegate, null, (String) args[0], (Headers) args[1], (byte[]) args[2]);
    } else if (method.getParameterCount() == 2 && method.getParameterTypes()[0] == Message.class) {
      natsRequest = NatsRequest.create(delegate, (Message) args[0]);
    }

    Context parentContext = Context.current();

    if (natsRequest == null || !producerInstrumenter.shouldStart(parentContext, natsRequest)) {
      return (Message) invokeMethod(method, delegate, args);
    }

    Context context = producerInstrumenter.start(parentContext, natsRequest);
    TimeoutException timeout = null;
    NatsRequest response = null;

    try (Scope ignored = context.makeCurrent()) {
      Message message = (Message) invokeMethod(method, delegate, args);

      if (message == null) {
        timeout = new TimeoutException("Timed out waiting for message");
      } else {
        response = NatsRequest.create(delegate, message);
      }

      return message;
    } finally {
      producerInstrumenter.end(context, natsRequest, response, timeout);
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
    NatsRequest natsRequest = null;

    if ((method.getParameterCount() == 2 || method.getParameterCount() == 3)
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == byte[].class) {
      natsRequest = NatsRequest.create(delegate, null, (String) args[0], null, (byte[]) args[1]);
    } else if ((method.getParameterCount() == 3 || method.getParameterCount() == 4)
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == Headers.class
        && method.getParameterTypes()[2] == byte[].class) {
      natsRequest =
          NatsRequest.create(delegate, null, (String) args[0], (Headers) args[1], (byte[]) args[2]);
    } else if ((method.getParameterCount() == 1 || method.getParameterCount() == 2)
        && method.getParameterTypes()[0] == Message.class) {
      natsRequest = NatsRequest.create(delegate, (Message) args[0]);
    }

    Context parentContext = Context.current();

    if (natsRequest == null || !producerInstrumenter.shouldStart(parentContext, natsRequest)) {
      return (CompletableFuture<Message>) invokeMethod(method, delegate, args);
    }

    NatsRequest notNullNatsRequest = natsRequest;
    Context context = producerInstrumenter.start(parentContext, notNullNatsRequest);

    return ((CompletableFuture<Message>) invokeMethod(method, delegate, args))
        .whenComplete(
            (message, exception) -> {
              if (message != null) {
                NatsRequest response = NatsRequest.create(delegate, message);
                producerInstrumenter.end(context, notNullNatsRequest, response, exception);
              } else {
                producerInstrumenter.end(context, notNullNatsRequest, null, exception);
              }
            });
  }

  // public Dispatcher createDispatcher()
  // public Dispatcher createDispatcher(MessageHandler messageHandler)
  private Dispatcher createDispatcher(Method method, Object[] args) throws Throwable {
    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == MessageHandler.class) {
      args[0] =
          new OpenTelemetryMessageHandler(
              (MessageHandler) args[0], consumerReceiveInstrumenter, consumerProcessInstrumenter);
    }

    Dispatcher wrapped = (Dispatcher) invokeMethod(method, delegate, args);
    return OpenTelemetryDispatcher.wrap(
        wrapped, consumerReceiveInstrumenter, consumerProcessInstrumenter);
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
