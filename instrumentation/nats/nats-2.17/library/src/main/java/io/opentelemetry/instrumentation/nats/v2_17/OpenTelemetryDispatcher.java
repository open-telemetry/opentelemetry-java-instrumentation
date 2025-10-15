/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import io.opentelemetry.instrumentation.nats.v2_17.internal.OpenTelemetryMessageHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class OpenTelemetryDispatcher implements InvocationHandler {

  private final Dispatcher delegate;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryDispatcher(
      Dispatcher delegate, Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = delegate;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  public static Dispatcher wrap(
      Dispatcher delegate, Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    return (Dispatcher)
        Proxy.newProxyInstance(
            OpenTelemetryDispatcher.class.getClassLoader(),
            new Class<?>[] {Dispatcher.class},
            new OpenTelemetryDispatcher(delegate, consumerProcessInstrumenter));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("subscribe".equals(method.getName()) && method.getReturnType().equals(Subscription.class)) {
      return subscribe(method, args);
    }

    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static Object invokeMethod(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  // Subscription subscribe(String subject, MessageHandler handler);
  // Subscription subscribe(String subject, String queue, MessageHandler handler);
  private Subscription subscribe(Method method, Object[] args) throws Throwable {
    if (method.getParameterCount() == 2 && method.getParameterTypes()[1] == MessageHandler.class) {
      args[1] =
          new OpenTelemetryMessageHandler((MessageHandler) args[1], consumerProcessInstrumenter);
    } else if (method.getParameterCount() == 3
        && method.getParameterTypes()[2] == MessageHandler.class) {
      args[2] =
          new OpenTelemetryMessageHandler((MessageHandler) args[2], consumerProcessInstrumenter);
    }

    return (Subscription) invokeMethod(method, delegate, args);
  }

  Dispatcher getDelegate() {
    return delegate;
  }
}
