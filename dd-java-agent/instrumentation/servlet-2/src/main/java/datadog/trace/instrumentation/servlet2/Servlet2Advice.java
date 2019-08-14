package datadog.trace.instrumentation.servlet2;

import static datadog.trace.agent.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.servlet2.Servlet2Decorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.security.Principal;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class Servlet2Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object servlet,
      @Advice.Argument(0) final ServletRequest req,
      @Advice.Argument(value = 1, readOnly = false, typing = Assigner.Typing.DYNAMIC)
          ServletResponse resp) {
    final Object spanAttr = req.getAttribute(DD_SPAN_ATTRIBUTE);
    if (!(req instanceof HttpServletRequest) || spanAttr != null) {
      // Tracing might already be applied by the FilterChain.  If so ignore this.
      return null;
    }

    if (resp instanceof HttpServletResponse) {
      resp = new StatusSavingHttpServletResponseWrapper((HttpServletResponse) resp);
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
            .ignoreActiveSpan()
            .asChildOf(extractedContext)
            .withTag("span.origin.type", servlet.getClass().getName())
            .startActive(true);

    final Span span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, httpServletRequest);
    DECORATE.onRequest(span, httpServletRequest);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    req.setAttribute(DD_SPAN_ATTRIBUTE, span);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Span currentSpan = GlobalTracer.get().activeSpan();
    if (currentSpan != null) {
      if (request instanceof HttpServletRequest) {
        final Principal principal = ((HttpServletRequest) request).getUserPrincipal();
        if (principal != null) {
          currentSpan.setTag(DDTags.USER_NAME, principal.getName());
        }
      }
    }

    if (scope != null) {
      final Span span = scope.span();
      DECORATE.onResponse(span, response);
      if (throwable != null) {
        if (response instanceof StatusSavingHttpServletResponseWrapper
            && ((StatusSavingHttpServletResponseWrapper) response).status
                == HttpServletResponse.SC_OK) {
          // exception was thrown but status code wasn't set
          Tags.HTTP_STATUS.set(span, 500);
        }
        DECORATE.onError(span, throwable);
      }
      DECORATE.beforeFinish(span);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(false);
      }
      scope.close();
    }
  }
}
