package io.opentelemetry.auto.typed.server.http;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.typed.base.DelegatingSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;

public class HttpServerSpan extends DelegatingSpan {

  private HttpServerSpan(Span delegate) {
    super(delegate);
  }

  public TraceId getTraceId() {
    return delegate.getContext().getTraceId();
  }

  public SpanId getSpanId() {
    return delegate.getContext().getSpanId();
  }

  //TODO review network attributes
  public HttpServerSpan setPeerIp(String netPeerIp) {
    SemanticAttributes.NET_PEER_IP.set(delegate, netPeerIp);
    return this;
  }

  public void setPeerPort(Integer port) {
    //TODO check this null handling and write documentation
    // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
    if (port != null && port > 0) {
      SemanticAttributes.NET_PEER_PORT.set(delegate, port);
    }
  }

  public void setMethod(String method) {
    SemanticAttributes.HTTP_METHOD.set(delegate, method);
  }

  //TODO Specification does not recommend using this attribute
  //See https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#http-server-semantic-conventions
  public void setUrl(String url) {
    SemanticAttributes.HTTP_URL.set(delegate, url);
  }
//See TODO above
//  public void setUrl(String scheme, String host, String target){
//    SemanticAttributes.HTTP_SCHEME.set(delegate, scheme);
//    SemanticAttributes.HTTP_HOST.set(delegate, host);
//    SemanticAttributes.HTTP_TARGET.set(delegate, target);
//  }
//public void setUrl(String scheme, String serverName, Integer port, String target){
//  SemanticAttributes.HTTP_SCHEME.set(delegate, scheme);
//  SemanticAttributes.HTTP_SERVER_NAME.set(delegate, serverName);
//  SemanticAttributes.NET_HOST_PORT.set(delegate, port);
//  SemanticAttributes.HTTP_TARGET.set(delegate, target);
//}

  public void setTarget(String target) {
    SemanticAttributes.HTTP_TARGET.set(delegate, target);
  }

  public void setHost(String host) {
    SemanticAttributes.HTTP_HOST.set(delegate, host);
  }

  public void setScheme(String scheme) {
    SemanticAttributes.HTTP_SCHEME.set(delegate, scheme);
  }

  public void setStatus(Status status) {
    delegate.setStatus(status);
  }

  public void setStatusCode(int code) {
    SemanticAttributes.HTTP_STATUS_CODE.set(delegate, code);
    //TODO think about it and at least document
    if (Config.get().getHttpServerErrorStatuses().contains(code)) {
      setStatus(Status.UNKNOWN);
    }
  }

  public void setStatusText(String statusLine) {
    SemanticAttributes.HTTP_STATUS_TEXT.set(delegate, statusLine);
  }

  public void setFlavor(String flavor) {
    SemanticAttributes.HTTP_FLAVOR.set(delegate, flavor);
  }

  public void setUserAgent(String userAgent) {
    SemanticAttributes.HTTP_USER_AGENT.set(delegate, userAgent);
  }

  public void setServerName(String serverName) {
    SemanticAttributes.HTTP_SERVER_NAME.set(delegate, serverName);
  }

  public void setRoute(String route) {
    SemanticAttributes.HTTP_ROUTE.set(delegate, route);
  }

  public void setClientIp(String clientIp) {
    SemanticAttributes.HTTP_CLIENT_IP.set(delegate, clientIp);
  }

  public void setError(final Throwable throwable) {
    delegate.setAttribute(MoreTags.ERROR_MSG, throwable.getMessage());
    delegate.setAttribute(MoreTags.ERROR_TYPE, throwable.getClass().getName());

    final StringWriter errorString = new StringWriter();
    throwable.printStackTrace(new PrintWriter(errorString));
    delegate.setAttribute(MoreTags.ERROR_STACK, errorString.toString());
  }

  public static HttpServerSpan create(Tracer tracer, String spanName, SpanContext parent) {
    return new HttpServerSpan(tracer
        .spanBuilder(spanName)
        .setSpanKind(Kind.SERVER)
        .setParent(parent)
        .startSpan());
  }

  public void end(int responseStatus, Throwable throwable) {
    setStatusCode(responseStatus);
    //TODO status_message
    setError(throwable);
    delegate.end();
  }

  public void end(int responseStatus) {
    setStatusCode(responseStatus);
    delegate.end();
  }

  //TODO Only use if response status is uknown
  public void end() {
    delegate.end();
  }
}
