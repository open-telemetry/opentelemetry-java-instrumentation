package datadog.trace.instrumentation.jetty8;

import static datadog.trace.instrumentation.jetty8.JettyDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object source, @Advice.Argument(2) final HttpServletRequest req) {

    if (GlobalTracer.get().activeSpan() != null) {
      // Tracing might already be applied.  If so ignore this.
      return null;
    }

    final SpanContext extractedContext =
        GlobalTracer.get()
            .extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req));
    final Scope scope =
        GlobalTracer.get()
            .buildSpan("jetty.request")
            .asChildOf(extractedContext)
            .withTag("span.origin.type", source.getClass().getName())
            .startActive(false);

    final Span span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);
    DECORATE.onRequest(span, req);
    final String resourceName = req.getMethod() + " " + source.getClass().getName();
    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) final HttpServletRequest req,
      @Advice.Argument(3) final HttpServletResponse resp,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable) {
    if (scope != null) {
      final Span span = scope.span();
      if (req.getUserPrincipal() != null) {
        span.setTag(DDTags.USER_NAME, req.getUserPrincipal().getName());
      }
      if (throwable != null) {
        DECORATE.onResponse(span, resp);
        if (resp.getStatus() == HttpServletResponse.SC_OK) {
          // exception is thrown in filter chain, but status code is incorrect
          Tags.HTTP_STATUS.set(span, 500);
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
}
