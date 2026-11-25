/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.common.internal.ServletResponseContext;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Servlet5TelemetryFilter implements Filter {

  private final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter;
  private final boolean addTraceIdRequestAttribute;

  private final BodyCaptureConfig requestBodyCaptureConfig;

  public Servlet5TelemetryFilter(
      Instrumenter<
              ServletRequestContext<HttpServletRequest>,
              ServletResponseContext<HttpServletResponse>>
          instrumenter,
      boolean addTraceIdRequestAttribute,
      BodyCaptureConfig requestBodyCaptureConfig) {
    this.instrumenter = instrumenter;
    this.addTraceIdRequestAttribute = addTraceIdRequestAttribute;
    this.requestBodyCaptureConfig = requestBodyCaptureConfig;
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

    if (!instrumenter.shouldStart(parentContext, requestContext)) {
      filterChain.doFilter(httpRequest, httpResponse);
      return;
    }

    Context context = instrumenter.start(parentContext, requestContext);

    HttpServletRequest bodyCaptureRequest = null;
    if (requestBodyCaptureConfig.isEnabled() && Span.fromContext(context).isRecording()) {
      // only capture when enabled and span is being recorded
      HttpServletRequest wrapped =
          Servlet5BodyCaptureRequestWrapper.wrapIfNeeded(
              httpRequest, requestBodyCaptureConfig.getMaxSize());
      if (wrapped != httpRequest) {
        // the request was not already wrapped by a previous filter invocation
        bodyCaptureRequest = wrapped;
      }
      httpRequest = wrapped;
    }

    if (addTraceIdRequestAttribute) {
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
      // we do this e.g. so that servlet containers can use these values in their access logs
      httpRequest.setAttribute("trace_id", spanContext.getTraceId());
      httpRequest.setAttribute("span_id", spanContext.getSpanId());
    }

    OtelHttpServletRequest otelRequest =
        new OtelHttpServletRequest(
            httpRequest, bodyCaptureRequest, context, requestContext, responseContext);
    Throwable error = null;
    try (Scope ignore = context.makeCurrent()) {
      filterChain.doFilter(otelRequest, httpResponse);
    } catch (Throwable t) {
      error = t;
      throw t;
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
    // request that captures the body, null when body capture is not applied to this request
    @Nullable private final HttpServletRequest bodyCaptureRequest;
    private final Context context;
    private final ServletRequestContext<HttpServletRequest> requestContext;
    private final ServletResponseContext<HttpServletResponse> responseContext;
    private boolean hasAsyncListener = false;
    @Nullable private Throwable asyncException;

    OtelHttpServletRequest(
        HttpServletRequest request,
        @Nullable HttpServletRequest bodyCaptureRequest,
        Context context,
        ServletRequestContext<HttpServletRequest> requestContext,
        ServletResponseContext<HttpServletResponse> responseContext) {
      super(request);
      this.bodyCaptureRequest = bodyCaptureRequest;
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
      ServletRequest request = delegate.getRequest();
      if (otelRequest.bodyCaptureRequest != null && delegate.hasOriginalRequestAndResponse()) {
        // The container returns the request it owns, which bypasses the wrapper that captures the
        // body. This only applies when the application did not provide its own request.
        return otelRequest.bodyCaptureRequest;
      }
      return request;
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
      delegate.start(() -> {
        try (Scope ignored = context.makeCurrent()) {
          runnable.run();
        } catch (Throwable t) {
          otelRequest.asyncException = t;
          throw t;
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
