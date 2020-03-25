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
package io.opentelemetry.auto.instrumentation.jetty;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.jetty.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.jetty.JettyDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jetty.JettyDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(
      @Advice.This final Object source, @Advice.Argument(2) final HttpServletRequest req) {

    if (req.getAttribute(SPAN_ATTRIBUTE) != null) {
      // Request already being traced elsewhere.
      return null;
    }

    final Span.Builder spanBuilder =
        TRACER.spanBuilder(req.getMethod() + " " + source.getClass().getName()).setSpanKind(SERVER);
    final SpanContext extractedContext = TRACER.getHttpTextFormat().extract(req, GETTER);
    if (extractedContext.isValid()) {
      spanBuilder.setParent(extractedContext);
    } else {
      // explicitly setting "no parent" in case a span was propagated to this thread
      // by the java-concurrent instrumentation when the thread was started
      spanBuilder.setNoParent();
    }
    final Span span = spanBuilder.startSpan();

    span.setAttribute("span.origin.type", source.getClass().getName());
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);
    DECORATE.onRequest(span, req);

    req.setAttribute(SPAN_ATTRIBUTE, span);
    req.setAttribute("traceId", span.getContext().getTraceId().toLowerBase16());
    req.setAttribute("spanId", span.getContext().getSpanId().toLowerBase16());
    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) final HttpServletRequest req,
      @Advice.Argument(3) final HttpServletResponse resp,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable) {
    if (spanWithScope == null) {
      return;
    }
    final Span span = spanWithScope.getSpan();
    final Principal userPrincipal = req.getUserPrincipal();
    if (userPrincipal != null) {
      span.setAttribute(MoreTags.USER_NAME, userPrincipal.getName());
    }
    if (throwable != null) {
      DECORATE.onResponse(span, resp);
      if (resp.getStatus() == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        span.setAttribute(Tags.HTTP_STATUS, 500);
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
