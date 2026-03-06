/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JettyClientWrapUtil {

  // Response.DemandedContentListener was added in Jetty 9.4.24. In versions 9.4.24–9.4.43,
  // AsyncContentListener and ContentListener do NOT extend DemandedContentListener, but Jetty's
  // HttpReceiver.ContentListeners filters via instanceof DemandedContentListener. Without
  // explicitly including it, the proxy fails the instanceof check and content is never delivered.
  @Nullable
  private static final Class<?> demandedContentListenerClass = loadDemandedContentListener();

  private static final Class<?>[] listenerInterfaces = buildListenerInterfaces();

  @Nullable
  private static Class<?> loadDemandedContentListener() {
    try {
      return Class.forName("org.eclipse.jetty.client.api.Response$DemandedContentListener");
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static Class<?>[] buildListenerInterfaces() {
    List<Class<?>> interfaces = new ArrayList<>();
    interfaces.add(Response.CompleteListener.class);
    interfaces.add(Response.FailureListener.class);
    interfaces.add(Response.SuccessListener.class);
    interfaces.add(Response.AsyncContentListener.class);
    interfaces.add(Response.ContentListener.class);
    interfaces.add(Response.HeadersListener.class);
    interfaces.add(Response.HeaderListener.class);
    interfaces.add(Response.BeginListener.class);
    if (demandedContentListenerClass != null) {
      interfaces.add(demandedContentListenerClass);
    }
    return interfaces.toArray(new Class<?>[0]);
  }

  private JettyClientWrapUtil() {}

  /**
   * Utility to wrap the response listeners only, this includes the important CompleteListener.
   *
   * @param parentContext top level context that is above the Jetty client span context
   * @param listeners all listeners passed to Jetty client send() method
   * @return list of wrapped ResponseListeners
   */
  public static List<Response.ResponseListener> wrapResponseListeners(
      Context parentContext, List<Response.ResponseListener> listeners) {

    return listeners.stream()
        .map(listener -> wrapTheListener(listener, parentContext))
        .collect(toList());
  }

  private static Response.ResponseListener wrapTheListener(
      Response.ResponseListener listener, Context context) {
    if (listener == null || listener instanceof JettyClientTracingListener) {
      return listener;
    }

    Class<?> listenerClass = listener.getClass();
    List<Class<?>> interfaces = new ArrayList<>();
    for (Class<?> type : listenerInterfaces) {
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
              } catch (InvocationTargetException exception) {
                throw exception.getCause();
              }
            });
  }
}
