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
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.thread.Invocable;

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
                if (method.getName().equals("onContentSource")) {
                  args[1] = wrapContentSource((Content.Source) args[1], context);
                }
                return method.invoke(listener, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  public static Content.Source wrapContentSource(Content.Source source, Context context) {
    if (source instanceof ContextAwareContentSource) {
      return source;
    }
    return new ContextAwareContentSource(source, context);
  }

  private static final class ContextAwareContentSource implements Content.Source, Invocable {
    private final Content.Source delegate;
    private final Context context;

    private ContextAwareContentSource(Content.Source delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public InvocationType getInvocationType() {
      return Invocable.getInvocationType(delegate);
    }

    @Override
    public Content.Chunk read() {
      return delegate.read();
    }

    @Override
    public void demand(Runnable callback) {
      delegate.demand(
          Invocable.from(Invocable.getInvocationType(callback), context.wrap(callback)));
    }

    @Override
    public long getLength() {
      return delegate.getLength();
    }

    @Override
    public boolean rewind() {
      return delegate.rewind();
    }

    @Override
    public void fail(Throwable failure) {
      delegate.fail(failure);
    }

    @Override
    public void fail(Throwable failure, boolean close) {
      delegate.fail(failure, close);
    }
  }
}
