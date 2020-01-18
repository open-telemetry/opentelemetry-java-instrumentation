package io.opentelemetry.auto.instrumentation.servlet3;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet3.HttpServletRequestExtractAdapter.GETTER;
import static io.opentelemetry.auto.instrumentation.servlet3.Servlet3Decorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet3.Servlet3Decorator.TRACER;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
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
  public static SpanScopePair onEnter(
      @Advice.This final Object servlet, @Advice.Argument(0) final ServletRequest request) {
    final Span current = TRACER.getCurrentSpan();
    System.out.println("Current: " + current + " isValid: " + current.getContext().isValid());
    final boolean hasActiveTrace = current != null && current.getContext().isValid();
    System.out.println(request.getAttribute(SPAN_ATTRIBUTE));
    final boolean hasServletTrace = request.getAttribute(SPAN_ATTRIBUTE) instanceof Span;
    final boolean invalidRequest = !(request instanceof HttpServletRequest);
    System.out.println(
        "hasActiveTrace: "
            + hasActiveTrace
            + " hasServletTrace: "
            + hasServletTrace
            + " invalidRequest: "
            + invalidRequest);
    if (invalidRequest || (hasActiveTrace && hasServletTrace)) {
      // Tracing might already be applied by the FilterChain.  If so ignore this.
      return null;
    }

    new Exception().printStackTrace();
    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    final Span.Builder builder = TRACER.spanBuilder("servlet.request");
    try {
      final SpanContext extractedContext =
          TRACER.getHttpTextFormat().extract((HttpServletRequest) request, GETTER);
      System.out.println("!!!!!!!!!!! Found extracted context");
      builder.setParent(extractedContext);
    } catch (final IllegalArgumentException e) {
      // Couldn't extract a context. We should treat this as a root span. '
      System.out.println("??????????? Didn't find extracted context");
      builder.setNoParent();
    }

    final Span span = builder.startSpan();
    span.setAttribute("span.origin.type", servlet.getClass().getName());

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    httpServletRequest.setAttribute(SPAN_ATTRIBUTE, span);

    return new SpanScopePair(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final SpanScopePair scope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span && request instanceof HttpServletRequest) {
      final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
      if (principal != null) {
        ((Span) spanAttr).setAttribute(MoreTags.USER_NAME, principal.getName());
      }
    }

    if (scope == null) {
      return;
    }

    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      final HttpServletRequest req = (HttpServletRequest) request;
      final HttpServletResponse resp = (HttpServletResponse) response;

      final Span span = scope.getSpan();

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
      scope.getScope().close();
    }
  }
}
