package io.opentelemetry.auto.instrumentation.jetty8;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.propagate;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.jetty8.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.jetty8.JettyDecorator.DECORATE;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(
      @Advice.This final Object source, @Advice.Argument(2) final HttpServletRequest req) {

    if (req.getAttribute(SPAN_ATTRIBUTE) != null) {
      // Request already being traced elsewhere.
      return null;
    }

    final AgentSpan.Context extractedContext = propagate().extract(req, GETTER);

    final AgentSpan span =
        startSpan("jetty.request", extractedContext)
            .setAttribute("span.origin.type", source.getClass().getName());
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);
    DECORATE.onRequest(span, req);
    final String resourceName = req.getMethod() + " " + source.getClass().getName();
    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);

    final AgentScope scope = activateSpan(span, false);
    req.setAttribute(SPAN_ATTRIBUTE, span);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) final HttpServletRequest req,
      @Advice.Argument(3) final HttpServletResponse resp,
      @Advice.Enter final AgentScope scope,
      @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    final AgentSpan span = scope.span();
    if (req.getUserPrincipal() != null) {
      span.setAttribute(MoreTags.USER_NAME, req.getUserPrincipal().getName());
    }
    if (throwable != null) {
      DECORATE.onResponse(span, resp);
      if (resp.getStatus() == HttpServletResponse.SC_OK) {
        // exception is thrown in filter chain, but status code is incorrect
        span.setAttribute(Tags.HTTP_STATUS, 500);
        span.setError(true);
      }
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
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
        span.finish(); // Finish the span manually since finishSpanOnClose was false
      }
    }
    scope.close();
  }
}
