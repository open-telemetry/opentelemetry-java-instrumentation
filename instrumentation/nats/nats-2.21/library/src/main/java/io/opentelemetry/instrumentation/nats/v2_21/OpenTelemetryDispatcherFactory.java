/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.nats.client.MessageHandler;
import io.nats.client.impl.DispatcherFactory;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsRequest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class OpenTelemetryDispatcherFactory implements InvocationHandler {

  private final DispatcherFactory delegate;
  private final Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter;
  private final Instrumenter<NatsRequest, Void> consumerProcessInstrumenter;

  public OpenTelemetryDispatcherFactory(
      DispatcherFactory delegate,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    this.delegate = delegate;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
  }

  public static DispatcherFactory wrap(
      DispatcherFactory delegate,
      Instrumenter<NatsRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<NatsRequest, Void> consumerProcessInstrumenter) {
    return (DispatcherFactory)
        Proxy.newProxyInstance(
            OpenTelemetryDispatcherFactory.class.getClassLoader(),
            new Class<?>[] {DispatcherFactory.class},
            new OpenTelemetryDispatcherFactory(
                delegate, consumerReceiveInstrumenter, consumerProcessInstrumenter));
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("createDispatcher".equals(method.getName())) {
      return createDispatcher(method, args);
    }

    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static Object invokeProxyMethod(Method method, Object target, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  // NatsDispatcher createDispatcher(NatsConnection conn, MessageHandler handler)
  private Object createDispatcher(Method method, Object[] args) throws Throwable {
    if (method.getParameterCount() == 2 && method.getParameterTypes()[1] == MessageHandler.class) {
      args[1] =
          new OpenTelemetryMessageHandler(
              (MessageHandler) args[1], consumerReceiveInstrumenter, consumerProcessInstrumenter);
    }

    return invokeProxyMethod(method, delegate, args);
  }
}
