/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.JetStreamReader;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetrySubscription implements InvocationHandler {

  private final Subscription delegate;
  private final Instrumenter<NatsRequest, NatsRequest> settleInstrumenter;

  // The proxy implements the same subscription interface as the delegate.
  @SuppressWarnings("unchecked")
  public static <T extends Subscription> T wrap(
      T subscription, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    if (Proxy.isProxyClass(subscription.getClass())
        && Proxy.getInvocationHandler(subscription) instanceof OpenTelemetrySubscription) {
      return subscription;
    }
    Class<?> subscriptionType =
        subscription instanceof JetStreamSubscription
            ? JetStreamSubscription.class
            : Subscription.class;
    return (T)
        Proxy.newProxyInstance(
            Subscription.class.getClassLoader(),
            new Class<?>[] {subscriptionType},
            new OpenTelemetrySubscription(subscription, settleInstrumenter));
  }

  public static Subscription unwrap(Subscription subscription) {
    if (Proxy.isProxyClass(subscription.getClass())
        && Proxy.getInvocationHandler(subscription) instanceof OpenTelemetrySubscription) {
      return ((OpenTelemetrySubscription) Proxy.getInvocationHandler(subscription)).delegate;
    }
    return subscription;
  }

  private OpenTelemetrySubscription(
      Subscription delegate, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    this.delegate = delegate;
    this.settleInstrumenter = settleInstrumenter;
  }

  @Override
  public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
    Object result = invokeMethod(method, delegate, args);
    if (method.getName().equals("nextMessage") && result instanceof Message) {
      return wrapMessage((Message) result);
    }
    if (method.getName().equals("fetch") && result instanceof List<?>) {
      return wrapMessages((List<?>) result);
    }
    if (method.getName().equals("iterate") && result instanceof Iterator<?>) {
      return wrapMessages((Iterator<?>) result);
    }
    if (method.getName().equals("reader") && result instanceof JetStreamReader) {
      return wrapReader((JetStreamReader) result, settleInstrumenter);
    }
    return result;
  }

  private List<Message> wrapMessages(List<?> messages) {
    List<Message> result = new ArrayList<>(messages.size());
    for (Object message : messages) {
      result.add(wrapMessage((Message) message));
    }
    return result;
  }

  private Iterator<Message> wrapMessages(Iterator<?> messages) {
    return new Iterator<Message>() {
      @Override
      public boolean hasNext() {
        return messages.hasNext();
      }

      @Override
      public Message next() {
        return wrapMessage((Message) messages.next());
      }

      @Override
      public void remove() {
        messages.remove();
      }
    };
  }

  private Message wrapMessage(@Nullable Message message) {
    return message == null ? null : OpenTelemetryMessage.wrap(message, settleInstrumenter);
  }

  private static JetStreamReader wrapReader(
      JetStreamReader reader, Instrumenter<NatsRequest, NatsRequest> settleInstrumenter) {
    return (JetStreamReader)
        Proxy.newProxyInstance(
            JetStreamReader.class.getClassLoader(),
            new Class<?>[] {JetStreamReader.class},
            (proxy, method, args) -> {
              Object result = invokeMethod(method, reader, args);
              return method.getName().equals("nextMessage") && result instanceof Message
                  ? OpenTelemetryMessage.wrap((Message) result, settleInstrumenter)
                  : result;
            });
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
