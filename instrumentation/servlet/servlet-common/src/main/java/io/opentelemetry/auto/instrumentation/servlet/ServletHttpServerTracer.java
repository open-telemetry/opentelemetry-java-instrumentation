package io.opentelemetry.auto.instrumentation.servlet;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.typed.server.http.HttpServerSpan;
import io.opentelemetry.auto.typed.server.http.HttpServerTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ServletHttpServerTracer<RESPONSE> extends
    HttpServerTracer<HttpServletRequest, RESPONSE> {

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

  private String getSpanName(Method method) {
    return spanNameForMethod(method);
  }

  @Override
  protected HttpServerSpan findExistingSpan(HttpServletRequest request) {
    Object span = request.getAttribute(SPAN_ATTRIBUTE);
    return span instanceof HttpServerSpan ? (HttpServerSpan) span : null;
  }

  @Override
  protected void persistSpanToRequest(HttpServerSpan span, HttpServletRequest request) {
    request.setAttribute(SPAN_ATTRIBUTE, span);
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

  @Override
  protected String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  public void onRequest(HttpServerSpan span, HttpServletRequest request) {
    //TODO why?
    request.setAttribute("traceId", span.getTraceId().toLowerBase16());
    request.setAttribute("spanId", span.getSpanId().toLowerBase16());

    //TODO why? they are not in semantic convention, right?
    span.setAttribute("servlet.path", request.getServletPath());
    span.setAttribute("servlet.context", request.getContextPath());
    //TODO what does the following line do?
//    DECORATE.onContext(span, request, request.getServletContext());
    super.onRequest(span, request);
  }

  @Override
  protected Getter<HttpServletRequest> getGetter() {
    return new HttpServletRequestGetter();
  }

  public static class HttpServletRequestGetter implements
      HttpTextFormat.Getter<HttpServletRequest> {
    @Override
    public String get(HttpServletRequest carrier, String key) {
      return carrier.getHeader(key);
    }
  }

  protected Throwable unwrapThrowable(Throwable throwable) {
    Throwable result = throwable;
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      result = throwable.getCause();
    }
    return super.unwrapThrowable(result);
  }

  public void setPrincipal(HttpServletRequest request) {
    final HttpServerSpan existingSpan = findExistingSpan(request);
    if (existingSpan != null) {
      final Principal principal = request.getUserPrincipal();
      if (principal != null) {
        existingSpan.setAttribute(MoreTags.USER_NAME, principal.getName());
      }
    }
  }
}
