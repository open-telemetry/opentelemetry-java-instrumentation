package io.opentelemetry.auto.instrumentation.jetty8;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.jetty8.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.jetty8.JettyDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jetty8.JettyDecorator.TRACER;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
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

    final Span.Builder spanBuilder = TRACER.spanBuilder("jetty.request");
    try {
      final SpanContext extractedContext = TRACER.getHttpTextFormat().extract(req, GETTER);
      spanBuilder.setParent(extractedContext);
    } catch (final IllegalArgumentException e) {
      // Couldn't extract a context. We should treat this as a root span.
      spanBuilder.setNoParent();
    }
    final Span span = spanBuilder.startSpan();

    span.setAttribute("span.origin.type", source.getClass().getName());
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);
    DECORATE.onRequest(span, req);
    final String resourceName = req.getMethod() + " " + source.getClass().getName();
    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);

    req.setAttribute(SPAN_ATTRIBUTE, span);
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
    if (req.getUserPrincipal() != null) {
      span.setAttribute(MoreTags.USER_NAME, req.getUserPrincipal().getName());
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
