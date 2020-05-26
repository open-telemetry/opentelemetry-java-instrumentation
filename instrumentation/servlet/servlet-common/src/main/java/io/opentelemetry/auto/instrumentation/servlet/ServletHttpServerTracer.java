package io.opentelemetry.auto.instrumentation.servlet;

import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ServletHttpServerTracer<RESPONSE> extends
    HttpServerTracer<HttpServletRequest> {
  protected static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected String getVersion() {
    return null;
  }

  @Override
  //TODO this violates convention
  protected URI url(HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  public SpanWithScope startSpan(HttpServletRequest request, String originType) {
    final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span) {
      /*
      Given request already has a span associated with it.
      As there should not be nested spans of kind SERVER, we should NOT create a new span here.

      But it may happen that there is no span in current Context or it is from a different trace.
      E.g. in case of async servlet request processing we create span for incoming request in one thread,
      but actual request continues processing happens in another thread.
      Depending on servlet container implementation, this processing may again arrive into this method.
      E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

      In this case we have to put the span from the request into current context before continuing.
      */
      final Span span = (Span) spanAttr;
      final boolean spanContextWasLost = !sameTrace(tracer.getCurrentSpan(), span);
      if (spanContextWasLost) {
        //Put span from request attribute into current context.
        //We did not create a new span here, so return null instead
        return new SpanWithScope(null, currentContextWith(span));
      } else {
        //We are inside nested servlet/filter, don't create new span
        return null;
      }
    }

    final Span.Builder builder = tracer.spanBuilder(getSpanName(request))
        .setSpanKind(SERVER)
        .setParent(extract(request, getGetter()))
        //TODO Where span.origin.type is defined?
        .setAttribute("span.origin.type", originType);

    Span span = builder.startSpan();
    //TODO fix parameter order
    onConnection(request, span);
    onRequest(span, request);

    request.setAttribute(SPAN_ATTRIBUTE, span);
    log.info("Set request attribute SPAN_ATTRIBUTE");
    request.setAttribute("traceId", span.getContext().getTraceId().toLowerBase16());
    request.setAttribute("spanId", span.getContext().getSpanId().toLowerBase16());

    return new SpanWithScope(span, currentContextWith(span));
  }

  private String getSpanName(Method method) {
    return spanNameForMethod(method);
  }

  private boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan.getContext().getTraceId().equals(otherSpan.getContext().getTraceId());
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    // HttpServletResponse doesn't have accessor for remote port prior to Servlet spec 3.0
    return null;
  }

  @Override
  protected String peerHostIP(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  public void onRequest(Span span, HttpServletRequest request) {
    span.setAttribute("servlet.path", request.getServletPath());
    span.setAttribute("servlet.context", request.getContextPath());
    //TODO what does the following line do?
//    DECORATE.onContext(span, request, request.getServletContext());
    super.onRequest(span, request);
  }

  private String getSpanName(HttpServletRequest request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    final String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }

  protected String method(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  protected HttpTextFormat.Getter<HttpServletRequest> getGetter() {
    return new HttpServletRequestGetter();
  }

  public static class HttpServletRequestGetter implements
      HttpTextFormat.Getter<HttpServletRequest> {
    @Override
    public String get(HttpServletRequest carrier, String key) {
      return carrier.getHeader(key);
    }
  }

  public void stopSpan(HttpServletRequest request, RESPONSE response,
      SpanWithScope spanWithScope, Throwable throwable) {
    // Set user.principal regardless of who created this span.
    final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
    if (spanAttr instanceof Span) {
      final Principal principal = request.getUserPrincipal();
      if (principal != null) {
        ((Span) spanAttr).setAttribute(MoreTags.USER_NAME, principal.getName());
      }
    }

    if (spanWithScope == null) {
      return;
    }

    final Span span = spanWithScope.getSpan();
    if (span == null) {
      // This was just a re-scoping of the current thread using the span in the request attribute
      // See comment in startSpan method above
      spanWithScope.closeScope();
      return;
    }

    onResponse(request, response, throwable, span);
    spanWithScope.closeScope();
  }

  //TODO ugly, think of better way
  protected void onResponse(HttpServletRequest request, RESPONSE response,
      Throwable throwable, Span span) {
    onResponse(response, throwable, span);
  }

  protected void onResponse(RESPONSE response, Throwable throwable, Span span) {
    int responseStatus = status(response);
    setStatus(span, responseStatus);
    if (throwable != null) {
      if (responseStatus == 200) {
        //TODO I think this is wrong.
        //We must report that response status that was actually sent to end user
        //We may change span status, but not http_status attribute
        setStatus(span, 500);
      }
      onError(span, unwrapThrowable(throwable));
    }
    span.end();
  }

  protected abstract int status(RESPONSE response);

  public void onTimeout(AtomicBoolean responseHandled, Span span, long timeout) {
    if (responseHandled.compareAndSet(false, true)) {
      span.setStatus(Status.DEADLINE_EXCEEDED);
      span.setAttribute("timeout", timeout);
      span.end();
    }
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    Throwable result = throwable;
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      result = throwable.getCause();
    }
    return super.unwrapThrowable(result);
  }

  private void setStatus(Span span, int status) {
    SemanticAttributes.HTTP_STATUS_CODE.set(span, status);
    //TODO status_message
    if (Config.get().getHttpServerErrorStatuses().contains(status)) {
      span.setStatus(Status.UNKNOWN);
    }
  }

}
