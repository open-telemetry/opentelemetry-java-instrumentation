/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * JettyClientTracingListener performs three actions when {@link #handleRequest(Context,
 * HttpRequest, Instrumenter)} is called 1. Start the CLIENT span 2. Set the listener callbacks for
 * each lifecycle action that signal end of the request 3. Wrap application request callbacks and
 * content providers with the caller context
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JettyClientTracingListener
    implements Request.FailureListener, Response.SuccessListener, Response.FailureListener {

  private static final Logger logger = Logger.getLogger(JettyClientTracingListener.class.getName());

  private static final Class<?>[] REQUEST_LISTENER_INTERFACES = {
    Request.BeginListener.class,
    Request.FailureListener.class,
    Request.SuccessListener.class,
    Request.HeadersListener.class,
    Request.ContentListener.class,
    Request.CommitListener.class,
    Request.QueuedListener.class
  };

  private final Context context;
  private final Instrumenter<Request, Response> instrumenter;

  private JettyClientTracingListener(
      Context context, Instrumenter<Request, Response> instrumenter) {
    this.context = context;
    this.instrumenter = instrumenter;
  }

  @Nullable
  public static Context handleRequest(
      Context parentContext, HttpRequest request, Instrumenter<Request, Response> instrumenter) {
    List<JettyClientTracingListener> current =
        request.getRequestListeners(JettyClientTracingListener.class);
    if (!current.isEmpty()) {
      logger.warning("A tracing request listener is already in place for this request!");
      return null;
    }

    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }

    Context context = instrumenter.start(parentContext, request);

    // wrap all important request-based listeners and request content that may already be attached
    List<Request.RequestListener> existingListeners = request.getRequestListeners(null);
    wrapRequestListeners(existingListeners, parentContext);
    wrapContentProvider(request, parentContext);

    JettyClientTracingListener listener = new JettyClientTracingListener(context, instrumenter);
    request.onRequestFailure(listener).onResponseFailure(listener).onResponseSuccess(listener);

    return context;
  }

  private static void wrapRequestListeners(
      List<Request.RequestListener> requestListeners, Context context) {
    ListIterator<Request.RequestListener> iterator = requestListeners.listIterator();

    while (iterator.hasNext()) {
      List<Class<?>> interfaces = new ArrayList<>(REQUEST_LISTENER_INTERFACES.length);
      Request.RequestListener listener = iterator.next();

      Class<?> listenerClass = listener.getClass();

      for (Class<?> type : REQUEST_LISTENER_INTERFACES) {
        if (type.isInstance(listener)) {
          interfaces.add(type);
        }
      }

      if (interfaces.isEmpty()) {
        continue;
      }

      Request.RequestListener proxiedListener =
          (Request.RequestListener)
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

      iterator.set(proxiedListener);
    }
  }

  private static void wrapContentProvider(HttpRequest request, Context context) {
    ContentProvider contentProvider = request.getContent();
    if (contentProvider == null) {
      return;
    }

    request.content((ContentProvider) wrapWithContext(contentProvider, context, true));
  }

  private static Object wrapWithContext(Object target, Context context, boolean wrapIterator) {
    List<Class<?>> interfaces = new ArrayList<>();
    addSupportedInterfaces(target.getClass(), interfaces, wrapIterator);
    return Proxy.newProxyInstance(
        target.getClass().getClassLoader(),
        interfaces.toArray(new Class<?>[0]),
        (proxy, method, args) -> {
          try (Scope ignored = context.makeCurrent()) {
            Object result = method.invoke(target, args);
            if (wrapIterator && result instanceof Iterator) {
              return wrapWithContext(result, context, false);
            }
            return result;
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        });
  }

  private static void addSupportedInterfaces(
      Class<?> type, List<Class<?>> interfaces, boolean wrapIterator) {
    if (type == null) {
      return;
    }
    for (Class<?> implementedInterface : type.getInterfaces()) {
      String name = implementedInterface.getName();
      boolean supported =
          wrapIterator
              ? name.equals("org.eclipse.jetty.client.api.ContentProvider")
                  || name.equals("org.eclipse.jetty.client.api.ContentProvider$Typed")
                  || name.equals("org.eclipse.jetty.client.AsyncContentProvider")
                  || name.equals("org.eclipse.jetty.client.Synchronizable")
                  || name.equals("org.eclipse.jetty.util.Callback")
                  || name.equals("java.io.Closeable")
              : name.equals("java.util.Iterator")
                  || name.equals("org.eclipse.jetty.client.Synchronizable")
                  || name.equals("org.eclipse.jetty.util.Callback")
                  || name.equals("java.io.Closeable");
      if (supported
          && Modifier.isPublic(implementedInterface.getModifiers())
          && !interfaces.contains(implementedInterface)) {
        interfaces.add(implementedInterface);
      }
      addSupportedInterfaces(implementedInterface, interfaces, wrapIterator);
    }
    addSupportedInterfaces(type.getSuperclass(), interfaces, wrapIterator);
  }

  @Override
  public void onSuccess(Response response) {
    instrumenter.end(this.context, response.getRequest(), response, null);
  }

  @Override
  public void onFailure(Request request, Throwable t) {
    instrumenter.end(this.context, request, null, t);
  }

  @Override
  public void onFailure(Response response, Throwable t) {
    instrumenter.end(this.context, response.getRequest(), response, t);
  }
}
