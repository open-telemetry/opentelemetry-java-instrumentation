package datadog.trace.instrumentation.servlet3;

import static datadog.trace.instrumentation.servlet3.Servlet3Decorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet3Advice {
  public static final String SERVLET_SPAN = "datadog.servlet.span";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object servlet, @Advice.Argument(0) final ServletRequest req) {
    final Object spanAttr = req.getAttribute(SERVLET_SPAN);
    if (!(req instanceof HttpServletRequest) || spanAttr != null) {
      // Tracing might already be applied by the FilterChain.  If so ignore this.
      return null;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) req;
    final SpanContext extractedContext =
        GlobalTracer.get()
            .extract(
                Format.Builtin.HTTP_HEADERS,
                new HttpServletRequestExtractAdapter(httpServletRequest));

    final Scope scope =
        GlobalTracer.get()
            .buildSpan("servlet.request")
            .asChildOf(extractedContext)
            .withTag("span.origin.type", servlet.getClass().getName())
            .startActive(false);

    final Span span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    req.setAttribute(SERVLET_SPAN, span);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Object spanAttr = request.getAttribute(SERVLET_SPAN);
    if (spanAttr instanceof Span && request instanceof HttpServletRequest) {
      final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
      if (principal != null) {
        ((Span) spanAttr).setTag(DDTags.USER_NAME, principal.getName());
      }
    }

    if (scope != null) {
      if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;
        final Span span = scope.span();

        if (throwable != null) {
          DECORATE.onResponse(span, resp);
          if (resp.getStatus() == HttpServletResponse.SC_OK) {
            // exception is thrown in filter chain, but status code is incorrect
            Tags.HTTP_STATUS.set(span, 500);
          }
          DECORATE.onError(span, throwable);
          DECORATE.beforeFinish(span);
          req.removeAttribute(SERVLET_SPAN);
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
            req.removeAttribute(SERVLET_SPAN);
            span.finish(); // Finish the span manually since finishSpanOnClose was false
          }
        }
        scope.close();
      }
    }
  }
}
