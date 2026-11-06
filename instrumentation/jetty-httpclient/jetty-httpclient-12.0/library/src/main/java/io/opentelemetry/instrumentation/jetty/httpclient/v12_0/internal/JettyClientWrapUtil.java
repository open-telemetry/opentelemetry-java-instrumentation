/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JettyClientWrapUtil {
  private static final Class<?>[] LISTENER_INTERFACES = {
    Response.CompleteListener.class,
    Response.FailureListener.class,
    Response.SuccessListener.class,
    Response.AsyncContentListener.class,
    Response.ContentSourceListener.class,
    Response.ContentListener.class,
    Response.HeadersListener.class,
    Response.HeaderListener.class,
    Response.BeginListener.class,
    Response.Listener.class
  };

  private JettyClientWrapUtil() {}

  @Nullable
  // The proxy implements the listener's interfaces, so it remains assignable to T.
  @SuppressWarnings("unchecked")
  public static <T extends Response.ResponseListener> T wrapListener(
      @Nullable T listener, Supplier<Context> contextSupplier) {
    if (listener == null || listener instanceof JettyClientTracingListener) {
      return listener;
    }

    Class<?> listenerClass = listener.getClass();
    List<Class<?>> interfaces = new ArrayList<>();
    for (Class<?> type : LISTENER_INTERFACES) {
      if (type.isInstance(listener)) {
        interfaces.add(type);
      }
    }
    if (interfaces.isEmpty()) {
      return listener;
    }

    return (T)
        Proxy.newProxyInstance(
            listenerClass.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            (proxy, method, args) -> {
              Context context = contextSupplier.get();
              try (Scope ignored = context.makeCurrent()) {
                return method.invoke(listener, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}
