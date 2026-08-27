/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetryJetStream implements InvocationHandler {

  private final JetStream delegate;
  private final Instrumenter<NatsRequest, NatsRequest> settleInstrumenter;

  public static JetStream wrap(
      JetStream jetStream, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    if (Proxy.isProxyClass(jetStream.getClass())
        && Proxy.getInvocationHandler(jetStream) instanceof OpenTelemetryJetStream) {
      return jetStream;
    }
    return (JetStream)
        Proxy.newProxyInstance(
            JetStream.class.getClassLoader(),
            new Class<?>[] {JetStream.class},
            new OpenTelemetryJetStream(jetStream, settleInstrumenter));
  }

  private OpenTelemetryJetStream(
      JetStream delegate, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    this.delegate = delegate;
    this.settleInstrumenter = settleInstrumenter;
  }

  @Override
  public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
    Object result = invokeMethod(method, delegate, args);
    if (method.getName().equals("subscribe") && result instanceof JetStreamSubscription) {
      return OpenTelemetrySubscription.wrap((JetStreamSubscription) result, settleInstrumenter);
    }
    return result;
  }

  private static Object invokeMethod(Method method, Object target, @Nullable Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
