package datadog.trace.instrumentation.jetty8;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
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
    final String resourceName = req.getMethod() + " " + source.getClass().getName();
    final Scope scope =
        GlobalTracer.get()
            .buildSpan("jetty.request")
            .asChildOf(extractedContext)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_SERVER)
            .withTag("servlet.context", req.getContextPath())
            .withTag("span.origin.type", source.getClass().getName())
            .startActive(false);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    final Span span = scope.span();
    Tags.COMPONENT.set(span, "jetty-handler");
    Tags.HTTP_METHOD.set(span, req.getMethod());
    Tags.HTTP_URL.set(span, req.getRequestURL().toString());
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
    if (req.getUserPrincipal() != null) {
      span.setTag("user.principal", req.getUserPrincipal().getName());
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
      if (throwable != null) {
        if (resp.getStatus() == HttpServletResponse.SC_OK) {
          // exception is thrown in filter chain, but status code is incorrect
          Tags.HTTP_STATUS.set(span, 500);
        }
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
        scope.close();
        span.finish(); // Finish the span manually since finishSpanOnClose was false
      } else if (req.isAsyncStarted()) {
        final AtomicBoolean activated = new AtomicBoolean(false);
        // what if async is already finished? This would not be called
        req.getAsyncContext().addListener(new TagSettingAsyncListener(activated, span));
        scope.close();
      } else {
        Tags.HTTP_STATUS.set(span, resp.getStatus());
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
        scope.close();
        span.finish(); // Finish the span manually since finishSpanOnClose was false
      }
    }
  }
}
