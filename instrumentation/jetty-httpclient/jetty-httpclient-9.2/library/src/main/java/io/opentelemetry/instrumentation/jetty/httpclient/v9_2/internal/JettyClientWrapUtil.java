/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import static java.util.Arrays.asList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.client.api.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JettyClientWrapUtil {

  private static final Class<?>[] LISTENER_INTERFACES = buildListenerInterfaces();

  private static Class<?>[] buildListenerInterfaces() {
    List<Class<?>> interfaces =
        new ArrayList<>(
            asList(
                Response.CompleteListener.class,
                Response.FailureListener.class,
                Response.SuccessListener.class,
                Response.AsyncContentListener.class,
                Response.ContentListener.class,
                Response.HeadersListener.class,
                Response.HeaderListener.class,
                Response.BeginListener.class));
    // Response.DemandedContentListener was added in Jetty 9.4.24. In versions 9.4.24–9.4.43,
    // AsyncContentListener and ContentListener do NOT extend DemandedContentListener, but Jetty's
    // HttpReceiver.ContentListeners filters via instanceof DemandedContentListener. Without
    // explicitly including it, the proxy fails the instanceof check and content is never delivered.
    try {
      interfaces.add(
          Class.forName("org.eclipse.jetty.client.api.Response$DemandedContentListener"));
    } catch (ClassNotFoundException ignored) {
      // ignored
    }
    return interfaces.toArray(new Class<?>[0]);
  }

  /**
   * Utility to wrap the response listeners only, this includes the important CompleteListener.
   *
   * @param parentContext top level context that is above the Jetty client span context
   * @param listeners all listeners passed to Jetty client send() method
   * @return list of wrapped ResponseListeners
   */
  public static List<Response.ResponseListener> wrapResponseListeners(
      Context parentContext, List<Response.ResponseListener> listeners) {
    List<Response.ResponseListener> wrappedListeners = new ArrayList<>(listeners.size());
    for (Response.ResponseListener listener : listeners) {
      wrappedListeners.add(wrapTheListener(listener, parentContext));
    }
    return wrappedListeners;
  }

  private static Response.ResponseListener wrapTheListener(
      Response.ResponseListener listener, Context context) {
    if (listener == null || listener instanceof JettyClientTracingListener) {
      return listener;
    }

    Class<?> listenerClass = listener.getClass();
    List<Class<?>> interfaces = new ArrayList<>(LISTENER_INTERFACES.length);
    for (Class<?> type : LISTENER_INTERFACES) {
      if (type.isInstance(listener)) {
        interfaces.add(type);
      }
    }
    if (interfaces.isEmpty()) {
      return listener;
    }

    return (Response.ResponseListener)
        Proxy.newProxyInstance(
            listenerClass.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            (proxy, method, args) -> {
              try (Scope ignored = context.makeCurrent()) {
                return method.invoke(listener, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  private JettyClientWrapUtil() {}
}
