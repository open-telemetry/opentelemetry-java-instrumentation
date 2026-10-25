/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpFields;
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
    if (source instanceof ContextAwareContentSource) {
      return source;
    }
    return new ContextAwareContentSource(source, context);
  }

  public static Request.Content wrapRequestContent(Request.Content content, Context context) {
    if (content instanceof ContextAwareRequestContent) {
      return content;
    }
    return new ContextAwareRequestContent(content, context);
  }

  public static Supplier<HttpFields> wrapRequestTrailers(
      Supplier<HttpFields> trailersSupplier, Context context) {
    if (trailersSupplier instanceof ContextAwareTrailersSupplier) {
      return trailersSupplier;
    }
    return new ContextAwareTrailersSupplier(trailersSupplier, context);
  }

  private static final class ContextAwareRequestContent implements Request.Content {
    private final Request.Content delegate;
    private final Context context;

    private ContextAwareRequestContent(Request.Content delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public Content.Chunk read() {
      try (Scope ignored = context.makeCurrent()) {
        Content.Chunk chunk = delegate.read();
        return chunk == null ? null : new ContextAwareChunk(chunk, context);
      }
    }

    @Override
    public void demand(Runnable callback) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.demand(context.wrap(callback));
      }
    }

    @Override
    public long getLength() {
      try (Scope ignored = context.makeCurrent()) {
        return delegate.getLength();
      }
    }

    @Override
    public boolean rewind() {
      try (Scope ignored = context.makeCurrent()) {
        return delegate.rewind();
      }
    }

    @Override
    public void fail(Throwable failure) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.fail(failure);
      }
    }

    @Override
    public void fail(Throwable failure, boolean close) {
      try (Scope ignored = context.makeCurrent()) {
        delegate.fail(failure, close);
      }
    }

    @Override
    public String getContentType() {
      try (Scope ignored = context.makeCurrent()) {
        return delegate.getContentType();
      }
    }
  }

  private static final class ContextAwareChunk implements Content.Chunk {
    private final Content.Chunk delegate;
    private final Context context;

    private ContextAwareChunk(Content.Chunk delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public ByteBuffer getByteBuffer() {
      return delegate.getByteBuffer();
    }

    @Override
    @Nullable
    public Throwable getFailure() {
      return delegate.getFailure();
    }

    @Override
    public boolean isLast() {
      return delegate.isLast();
    }

    @Override
    public boolean canRetain() {
      return delegate.canRetain();
    }

    @Override
    public void retain() {
      delegate.retain();
    }

    @Override
    public boolean release() {
      try (Scope ignored = context.makeCurrent()) {
        return delegate.release();
      }
    }
  }

  private static final class ContextAwareTrailersSupplier implements Supplier<HttpFields> {
    private final Supplier<HttpFields> delegate;
    private final Context context;

    private ContextAwareTrailersSupplier(Supplier<HttpFields> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public HttpFields get() {
      try (Scope ignored = context.makeCurrent()) {
        return delegate.get();
      }
    }
  }

  private static final class ContextAwareContentSource implements Content.Source {
    private final Content.Source delegate;
    private final Context context;

    private ContextAwareContentSource(Content.Source delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public Content.Chunk read() {
      return delegate.read();
    }

    @Override
    public void demand(Runnable callback) {
      delegate.demand(context.wrap(callback));
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
