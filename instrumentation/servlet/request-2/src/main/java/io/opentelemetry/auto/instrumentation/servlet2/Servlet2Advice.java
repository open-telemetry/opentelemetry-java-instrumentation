package io.opentelemetry.auto.instrumentation.servlet2;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet2.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.servlet2.Servlet2Decorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet2.Servlet2Decorator.TRACER;

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
    final Span current = TRACER.getCurrentSpan();
    final boolean hasActiveTrace = current.getContext().isValid();
    final boolean hasServletTrace = request.getAttribute(SPAN_ATTRIBUTE) instanceof Span;
    final boolean invalidRequest = !(request instanceof HttpServletRequest);
    if (invalidRequest || (hasActiveTrace && hasServletTrace)) {
      // Tracing might already be applied by the FilterChain.  If so ignore this.
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
        TRACER.spanBuilder("servlet.request").setSpanKind(Span.Kind.SERVER);
    try {
      final SpanContext extractedContext =
          TRACER.getHttpTextFormat().extract((HttpServletRequest) request, GETTER);
      builder.setParent(extractedContext);
    } catch (final IllegalArgumentException e) {
      // Couldn't extract a context. We should treat this as a root span. '
      builder.setNoParent();
    }

    final Span span = builder.startSpan();
    span.setAttribute("span.origin.type", servlet.getClass().getName());

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    httpServletRequest.setAttribute(SPAN_ATTRIBUTE, span);

    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final SpanWithScope spanAndScope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span && request instanceof HttpServletRequest) {
      final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
      if (principal != null) {
        ((Span) spanAttr).setAttribute(MoreTags.USER_NAME, principal.getName());
      }
    }

    if (spanAndScope == null) {
      return;
    }
    final Span span = spanAndScope.getSpan();
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
    spanAndScope.getScope().close();
  }
}
