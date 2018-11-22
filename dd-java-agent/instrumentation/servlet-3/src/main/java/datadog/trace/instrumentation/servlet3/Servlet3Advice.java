package datadog.trace.instrumentation.servlet3;

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
import java.security.Principal;
import java.util.Collections;
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
            .withTag(Tags.COMPONENT.getKey(), "java-web-servlet")
            .withTag(Tags.HTTP_METHOD.getKey(), httpServletRequest.getMethod())
            .withTag(Tags.HTTP_URL.getKey(), httpServletRequest.getRequestURL().toString())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
            .withTag("span.origin.type", servlet.getClass().getName())
            .withTag("servlet.context", httpServletRequest.getContextPath())
            .startActive(false);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    req.setAttribute(SERVLET_SPAN, scope.span());
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
          if (resp.getStatus() == HttpServletResponse.SC_OK) {
            // exception is thrown in filter chain, but status code is incorrect
            Tags.HTTP_STATUS.set(span, 500);
          }
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
          req.removeAttribute(SERVLET_SPAN);
          span.finish(); // Finish the span manually since finishSpanOnClose was false
        } else {
          final AtomicBoolean activated = new AtomicBoolean(false);
          if (req.isAsyncStarted()) {
            req.getAsyncContext().addListener(new TagSettingAsyncListener(activated, span));
          }
          // Check again in case the request finished before adding the listener.
          if (!req.isAsyncStarted() && activated.compareAndSet(false, true)) {
            Tags.HTTP_STATUS.set(span, resp.getStatus());
            req.removeAttribute(SERVLET_SPAN);
            span.finish(); // Finish the span manually since finishSpanOnClose was false
          }
        }
        scope.close();
      }
    }
  }
}
