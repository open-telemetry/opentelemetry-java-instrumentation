/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** A send's terminal lifecycle, independent of the calling thread's scope. */
public final class JmsSendOperation {
  private static final Logger logger = Logger.getLogger(JmsSendOperation.class.getName());

  private static final ClassValue<Class<?>> listenerTypes =
      new ClassValue<Class<?>>() {
        @Nullable
        @Override
        protected Class<?> computeValue(Class<?> producerType) {
          try {
            return Class.forName(
                producerType.getPackage().getName() + ".CompletionListener",
                false,
                producerType.getClassLoader());
          } catch (ClassNotFoundException e) {
            logger.log(FINE, "Failed to resolve JMS completion listener", e);
            return null;
          }
        }
      };

  private final Instrumenter<MessageWithDestination, Void> instrumenter;
  private final MessageWithDestination request;
  private final Context parentContext;
  private final Context context;
  private final AtomicBoolean ended = new AtomicBoolean();
  private boolean asynchronous;

  public JmsSendOperation(
      Instrumenter<MessageWithDestination, Void> instrumenter,
      MessageWithDestination request,
      Context parentContext,
      Context context) {
    this.instrumenter = instrumenter;
    this.request = request;
    this.parentContext = parentContext;
    this.context = context;
  }

  @Nullable
  public Object wrapListener(@Nullable Object listener, Class<?> producerType) {
    if (listener == null) {
      return null;
    }
    try {
      // Resolve the interface only on asynchronous sends. JMS 1.1 has no CompletionListener.
      Class<?> listenerType = listenerTypes.get(producerType);
      if (listenerType == null || !listenerType.isInstance(listener)) {
        return listener;
      }
      Object wrapped =
          Proxy.newProxyInstance(
              listenerType.getClassLoader(),
              new Class<?>[] {listenerType},
              (proxy, method, callbackArguments) -> {
                if (method.getName().equals("onCompletion")
                    || method.getName().equals("onException")) {
                  Throwable failure =
                      method.getName().equals("onException")
                          ? (Throwable) callbackArguments[1]
                          : null;
                  end(failure);
                  try (Scope ignored = parentContext.makeCurrent()) {
                    return invoke(listener, method, callbackArguments);
                  }
                }
                return invoke(listener, method, callbackArguments);
              });
      asynchronous = true;
      return wrapped;
    } catch (Throwable t) {
      logger.log(FINE, "Failed to wrap JMS completion listener", t);
      return listener;
    }
  }

  private static Object invoke(Object listener, Method method, @Nullable Object[] arguments)
      throws Throwable {
    try {
      return method.invoke(listener, arguments);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  public void endOnReturn(@Nullable Throwable failure) {
    if (!asynchronous || failure != null) {
      end(failure);
    }
  }

  private void end(@Nullable Throwable failure) {
    if (ended.compareAndSet(false, true)) {
      try {
        instrumenter.end(context, request, null, failure);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to end JMS send", t);
      }
    }
  }
}
