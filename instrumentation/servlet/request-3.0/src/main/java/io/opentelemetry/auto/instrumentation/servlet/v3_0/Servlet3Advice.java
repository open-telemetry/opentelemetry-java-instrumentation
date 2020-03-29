/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;
import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet.v3_0.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.servlet.v3_0.Servlet3Decorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.v3_0.Servlet3Decorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet3Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(
      @Advice.This final Object servlet,
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response) {
    if (!(request instanceof HttpServletRequest)) {
      return null;
    }
    final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span) {
      // inside of an existing servlet span already, possibly a dispatched servlet/filter

      final Span span = (Span) spanAttr;
      final boolean spanContextWasLost =
          !TRACER.getCurrentSpan().getContext().getTraceId().equals(span.getContext().getTraceId());
      if (spanContextWasLost) {
        // either there is no current span, or there is a current span but it is left over from some
        // other trace

        // re-scope the current work using the span in the request attribute
        return new SpanWithScope(null, currentContextWith(span));
      } else {
        // everything is good, just inside of a nested servlet/filter

        // do not capture anything
        return null;
      }
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    // For use by HttpServletResponseInstrumentation:
    InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class)
        .put((HttpServletResponse) response, httpServletRequest);

    final Span.Builder builder =
        TRACER.spanBuilder(DECORATE.spanNameForRequest(httpServletRequest)).setSpanKind(SERVER);
    builder.setParent(extract(httpServletRequest, GETTER));
    final Span span = builder.startSpan();
    span.setAttribute("span.origin.type", servlet.getClass().getName());

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    httpServletRequest.setAttribute(SPAN_ATTRIBUTE, span);
    httpServletRequest.setAttribute("traceId", span.getContext().getTraceId().toLowerBase16());
    httpServletRequest.setAttribute("spanId", span.getContext().getSpanId().toLowerBase16());

    return new SpanWithScope(span, currentContextWith(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span && request instanceof HttpServletRequest) {
      final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
      if (principal != null) {
        ((Span) spanAttr).setAttribute(MoreTags.USER_NAME, principal.getName());
      }
    }

    if (spanWithScope == null) {
      return;
    }

    final Span span = spanWithScope.getSpan();
    if (span == null) {
      // this was just a re-scoping of the current thread using the span in the request attribute
      spanWithScope.closeScope();
      return;
    }

    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      final HttpServletRequest req = (HttpServletRequest) request;
      final HttpServletResponse resp = (HttpServletResponse) response;

      if (throwable != null) {
        DECORATE.onResponse(span, resp);
        if (resp.getStatus() == HttpServletResponse.SC_OK) {
          // exception is thrown in filter chain, but status code is incorrect
          // exception was thrown but status code wasn't set
          span.setAttribute(Tags.HTTP_STATUS, 500);
          span.setStatus(Status.UNKNOWN);
        }
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end(); // Finish the span manually since finishSpanOnClose was false
      } else {
        final AtomicBoolean activated = new AtomicBoolean(false);
        if (req.isAsyncStarted()) {
          try {
            req.getAsyncContext().addListener(new TagSettingAsyncListener(activated, span));
          } catch (final IllegalStateException e) {
            // org.eclipse.jetty.server.Request may throw an exception here if request became
            // finished after check above. We just ignore that exception and move on.
          }
        }
        // Check again in case the request finished before adding the listener.
        if (!req.isAsyncStarted() && activated.compareAndSet(false, true)) {
          DECORATE.onResponse(span, resp);
          DECORATE.beforeFinish(span);
          span.end(); // Finish the span manually since finishSpanOnClose was false
        }
      }
      spanWithScope.closeScope();
    }
  }
}
