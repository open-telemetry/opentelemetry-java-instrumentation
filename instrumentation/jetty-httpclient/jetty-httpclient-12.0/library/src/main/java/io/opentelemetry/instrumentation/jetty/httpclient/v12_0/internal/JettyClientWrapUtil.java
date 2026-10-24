/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.io.Content;

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

  /**
   * Utility to wrap the response listeners only, this includes the important CompleteListener.
   *
   * @param context top level context that is above the Jetty client span context
   * @param listener listener passed to Jetty client send() method
   * @return wrapped listener
   */
  @Nullable
  public static Response.CompleteListener wrapTheListener(
      @Nullable Response.CompleteListener listener, Context context) {
    return wrapListener(listener, () -> context);
  }

  @Nullable
  // Callers use the supported response-listener interfaces implemented by the proxy.
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
                Object[] invocationArgs = args;
                if (method.getName().equals("onContentSource")
                    && args != null
                    && args.length == 2
                    && args[1] instanceof Content.Source) {
                  invocationArgs = args.clone();
                  invocationArgs[1] = wrapContentSource((Content.Source) args[1], context);
                }
                return method.invoke(listener, invocationArgs);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  public static Content.Source wrapContentSource(Content.Source source, Context context) {
    if (Proxy.isProxyClass(source.getClass())
        && Proxy.getInvocationHandler(source) instanceof ContentSourceInvocationHandler) {
      return source;
    }
    InvocationHandler handler = new ContentSourceInvocationHandler(source, context);
    return (Content.Source)
        Proxy.newProxyInstance(
            Content.Source.class.getClassLoader(), new Class<?>[] {Content.Source.class}, handler);
  }

  private static final class ContentSourceInvocationHandler implements InvocationHandler {
    private final Content.Source source;
    private final Context context;

    private ContentSourceInvocationHandler(Content.Source source, Context context) {
      this.source = source;
      this.context = context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      Object[] invocationArgs = args;
      if (method.getName().equals("demand")
          && args != null
          && args.length == 1
          && args[0] instanceof Runnable) {
        invocationArgs = args.clone();
        invocationArgs[0] = context.wrap((Runnable) args[0]);
      }
      try {
        return method.invoke(source, invocationArgs);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}
