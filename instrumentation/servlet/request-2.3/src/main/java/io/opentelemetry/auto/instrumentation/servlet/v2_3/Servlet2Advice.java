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
package io.opentelemetry.auto.instrumentation.servlet.v2_3;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet.v2_3.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.servlet.v2_3.Servlet2Decorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.v2_3.Servlet2Decorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;
import java.security.Principal;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class Servlet2Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(
      @Advice.This final Object servlet,
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(value = 1, readOnly = false, typing = Assigner.Typing.DYNAMIC)
          ServletResponse response) {
    final boolean hasServletTrace = request.getAttribute(SPAN_ATTRIBUTE) instanceof Span;
    final boolean invalidRequest = !(request instanceof HttpServletRequest);
    if (invalidRequest || hasServletTrace) {
      // Tracing might already be applied by the FilterChain or a parent request (forward/include).
      return null;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    if (response instanceof HttpServletResponse) {
      // For use by HttpServletResponseInstrumentation:
      InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class)
          .put((HttpServletResponse) response, httpServletRequest);

      response = new StatusSavingHttpServletResponseWrapper((HttpServletResponse) response);
    }

    final Span.Builder builder =
        TRACER.spanBuilder(DECORATE.spanNameForRequest(httpServletRequest)).setSpanKind(SERVER);
    final SpanContext extract = TRACER.getHttpTextFormat().extract(httpServletRequest, GETTER);
    if (extract.isValid()) {
      builder.setParent(extract);
    } else {
      // explicitly setting "no parent" in case a span was propagated to this thread
      // by the java-concurrent instrumentation when the thread was started
      builder.setNoParent();
    }

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
    DECORATE.onResponse(span, response);
    if (throwable != null) {
      if (response instanceof StatusSavingHttpServletResponseWrapper
          && ((StatusSavingHttpServletResponseWrapper) response).status
              == HttpServletResponse.SC_OK) {
        // exception was thrown but status code wasn't set
        span.setAttribute(Tags.HTTP_STATUS, 500);
        span.setStatus(Status.UNKNOWN);
      }
      DECORATE.onError(span, throwable);
    }
    DECORATE.beforeFinish(span);
    span.end();
    spanWithScope.closeScope();
  }
}
