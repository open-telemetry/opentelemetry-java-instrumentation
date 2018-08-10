package datadog.trace.instrumentation.servlet2;

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
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet2Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object servlet, @Advice.Argument(0) final ServletRequest req) {
    if (GlobalTracer.get().activeSpan() != null || !(req instanceof HttpServletRequest)) {
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
            .withTag(Tags.COMPONENT.getKey(), "java-web-servlet")
            .withTag(Tags.HTTP_METHOD.getKey(), httpServletRequest.getMethod())
            .withTag(Tags.HTTP_URL.getKey(), httpServletRequest.getRequestURL().toString())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
            .withTag("span.origin.type", servlet.getClass().getName())
            .withTag("servlet.context", httpServletRequest.getContextPath())
            .startActive(true);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    if (httpServletRequest.getUserPrincipal() != null) {
      scope.span().setTag("user.principal", httpServletRequest.getUserPrincipal().getName());
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable) {

    if (scope != null) {
      if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
        final Span span = scope.span();

        if (throwable != null) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }
      }
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(false);
      }
      scope.close();
    }
  }
}
