/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Servlet3TelemetryFilter implements Filter {

  private final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter;
  private final boolean addTraceIdRequestAttribute;

  public Servlet3TelemetryFilter(
      Instrumenter<
              ServletRequestContext<HttpServletRequest>,
              ServletResponseContext<HttpServletResponse>>
          instrumenter,
      boolean addTraceIdRequestAttribute) {
    this.instrumenter = instrumenter;
    this.addTraceIdRequestAttribute = addTraceIdRequestAttribute;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    // Only HttpServlets are supported.
    if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
      filterChain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    Context parentContext = Context.current();
    ServletRequestContext<HttpServletRequest> requestContext =
        new ServletRequestContext<>(httpRequest);
    ServletResponseContext<HttpServletResponse> responseContext =
        new ServletResponseContext<>(httpResponse);
    instrumenter.shouldStart(parentContext, requestContext);
    Context context = instrumenter.start(parentContext, requestContext);

    if (addTraceIdRequestAttribute) {
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
      // we do this e.g. so that servlet containers can use these values in their access logs
      request.setAttribute("trace_id", spanContext.getTraceId());
      request.setAttribute("span_id", spanContext.getSpanId());
    }

    OtelHttpServletRequest otelRequest =
        new OtelHttpServletRequest(httpRequest, context, requestContext, responseContext);
    Throwable error = null;
    try (Scope ignore = context.makeCurrent()) {
      filterChain.doFilter(otelRequest, httpResponse);
    } catch (Throwable throwable) {
      error = throwable;
      throw throwable;
    } finally {
      if (otelRequest.hasAsyncListener) {
        if (error != null) {
          otelRequest.asyncException = error;
        }
      } else {
        instrumenter.end(context, requestContext, responseContext, error);
      }
    }
  }

  @Override
  public void destroy() {}

  private class OtelHttpServletRequest extends HttpServletRequestWrapper {
    final Context context;
    final ServletRequestContext<HttpServletRequest> requestContext;
    final ServletResponseContext<HttpServletResponse> responseContext;
    boolean hasAsyncListener = false;
    Throwable asyncException;

    OtelHttpServletRequest(
        HttpServletRequest request,
        Context context,
        ServletRequestContext<HttpServletRequest> requestContext,
        ServletResponseContext<HttpServletResponse> responseContext) {
      super(request);
      this.context = context;
      this.requestContext = requestContext;
      this.responseContext = responseContext;
    }

    @Override
    public AsyncContext getAsyncContext() {
      return new OtelAsyncContext(super.getAsyncContext(), this);
    }

    @Override
    public AsyncContext startAsync() {
      AsyncContext asyncContext = new OtelAsyncContext(super.startAsync(), this);
      attachAsyncListener(asyncContext);
      return asyncContext;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
      AsyncContext asyncContext =
          new OtelAsyncContext(super.startAsync(servletRequest, servletResponse), this);
      attachAsyncListener(asyncContext);
      return asyncContext;
    }

    private void attachAsyncListener(AsyncContext asyncContext) {
      asyncContext.addListener(
          new AsyncListener() {
            private final AtomicBoolean responseHandled = new AtomicBoolean();

            @Override
            public void onComplete(AsyncEvent asyncEvent) {
              if (responseHandled.compareAndSet(false, true)) {
                instrumenter.end(context, requestContext, responseContext, asyncException);
              }
            }

            @Override
            public void onTimeout(AsyncEvent asyncEvent) {
              if (responseHandled.compareAndSet(false, true)) {
                responseContext.setTimeout(asyncEvent.getAsyncContext().getTimeout());
                instrumenter.end(context, requestContext, responseContext, asyncException);
              }
            }

            @Override
            public void onError(AsyncEvent asyncEvent) {
              if (responseHandled.compareAndSet(false, true)) {
                instrumenter.end(
                    context, requestContext, responseContext, asyncEvent.getThrowable());
              }
            }

            @Override
            public void onStartAsync(AsyncEvent asyncEvent) {}
          });

      this.hasAsyncListener = true;
    }
  }

  /** Delegates all methods except {@link #start(Runnable)} which wraps the {@link Runnable}. */
  private static class OtelAsyncContext implements AsyncContext {
    private final AsyncContext delegate;
    private final OtelHttpServletRequest otelRequest;

    OtelAsyncContext(AsyncContext delegate, OtelHttpServletRequest otelRequest) {
      this.delegate = delegate;
      this.otelRequest = otelRequest;
    }

    @Override
    public ServletRequest getRequest() {
      return delegate.getRequest();
    }

    @Override
    public ServletResponse getResponse() {
      return delegate.getResponse();
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
      return delegate.hasOriginalRequestAndResponse();
    }

    @Override
    public void dispatch() {
      delegate.dispatch();
    }

    @Override
    public void dispatch(String path) {
      delegate.dispatch(path);
    }

    @Override
    public void dispatch(ServletContext context, String path) {
      delegate.dispatch(context, path);
    }

    @Override
    public void complete() {
      delegate.complete();
    }

    @Override
    public void start(Runnable runnable) {
      Context context = Context.current();
      delegate.start(
          () -> {
            try (Scope ignored = context.makeCurrent()) {
              runnable.run();
            } catch (Throwable throwable) {
              otelRequest.asyncException = throwable;
            }
          });
    }

    @Override
    public void addListener(AsyncListener listener) {
      delegate.addListener(listener);
    }

    @Override
    public void addListener(
        AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
      delegate.addListener(listener, servletRequest, servletResponse);
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
      return delegate.createListener(clazz);
    }

    @Override
    public void setTimeout(long timeout) {
      delegate.setTimeout(timeout);
    }

    @Override
    public long getTimeout() {
      return delegate.getTimeout();
    }
  }
}
