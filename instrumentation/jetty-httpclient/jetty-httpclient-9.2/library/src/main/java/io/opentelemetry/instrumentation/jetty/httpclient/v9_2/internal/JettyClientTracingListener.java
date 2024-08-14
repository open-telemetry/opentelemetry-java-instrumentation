/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * JettyClientTracingListener performs three actions when {@link #handleRequest(Context,
 * HttpRequest, Instrumenter)} is called 1. Start the CLIENT span 2. Set the listener callbacks for
 * each lifecycle action that signal end of the request 3. Wrap request listeners to propagate
 * context into the listeners
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JettyClientTracingListener
    implements Request.FailureListener, Response.SuccessListener, Response.FailureListener {

  private static final Logger logger = Logger.getLogger(JettyClientTracingListener.class.getName());

  private static final Class<?>[] requestlistenerInterfaces = {
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

    // wrap all important request-based listeners that may already be attached, null should ensure
    // that all listeners are returned here
    List<Request.RequestListener> existingListeners = request.getRequestListeners(null);
    wrapRequestListeners(existingListeners, context);

    JettyClientTracingListener listener = new JettyClientTracingListener(context, instrumenter);
    request.onRequestFailure(listener).onResponseFailure(listener).onResponseSuccess(listener);

    return context;
  }

  private static void wrapRequestListeners(
      List<Request.RequestListener> requestListeners, Context context) {
    ListIterator<Request.RequestListener> iterator = requestListeners.listIterator();

    while (iterator.hasNext()) {
      List<Class<?>> interfaces = new ArrayList<>();
      Request.RequestListener listener = iterator.next();

      Class<?> listenerClass = listener.getClass();

      for (Class<?> type : requestlistenerInterfaces) {
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
                    } catch (InvocationTargetException exception) {
                      throw exception.getCause();
                    }
                  });

      iterator.set(proxiedListener);
    }
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
