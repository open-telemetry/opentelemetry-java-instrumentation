/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.logging.Logger;

/**
 * <b>Required attributes:</b>
 *
 * <ul>
 *   <li>faas.trigger: Type of the trigger on which the function is executed.
 *   <li>http.method: HTTP request method.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 *   <li>http.status_code: [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
 * </ul>
 *
 * <b>Additional constraints</b>
 *
 * <p>At least one of the following must be set:
 *
 * <ul>
 *   <li>http.url
 *   <li>http.scheme, http.host, http.target
 *   <li>http.scheme, http.server_name, net.host.port, http.target
 *   <li>http.scheme, net.host.name, net.host.port, http.target
 * </ul>
 */
public class FaasHttpSpan extends DelegatingSpan implements FaasHttpSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    FAAS_TRIGGER,
    FAAS_EXECUTION,
    HTTP_SERVER_NAME,
    HTTP_ROUTE,
    HTTP_CLIENT_IP,
    HTTP_METHOD,
    HTTP_URL,
    HTTP_TARGET,
    HTTP_HOST,
    HTTP_SCHEME,
    HTTP_STATUS_CODE,
    HTTP_STATUS_TEXT,
    HTTP_FLAVOR,
    HTTP_USER_AGENT,
    NET_TRANSPORT,
    NET_PEER_IP,
    NET_PEER_PORT,
    NET_PEER_NAME,
    NET_HOST_IP,
    NET_HOST_PORT,
    NET_HOST_NAME;
    

    @SuppressWarnings("ImmutableEnumChecker")
    private long flag;

    AttributeStatus() {
      this.flag = 1L << this.ordinal();
    }

    public boolean isSet(AttributeStatus attribute) {
      return (this.flag & attribute.flag) > 0;
    }

    public void set(AttributeStatus attribute) {
      this.flag |= attribute.flag;
    }

    public void set(long attribute) {
      this.flag = attribute;
    }

    public long getValue() {
      return flag;
    }
  }

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(FaasHttpSpan.class.getName());
  public final AttributeStatus status;

  protected FaasHttpSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

	/**
	 * Entry point to generate a {@link FaasHttpSpan}.
	 * @param tracer Tracer to use
	 * @param spanName Name for the {@link Span}
	 * @return a {@link FaasHttpSpan} object.
	 */
  public static FaasHttpSpanBuilder createFaasHttpSpanBuilder(Tracer tracer, String spanName) {
    return new FaasHttpSpanBuilder(tracer, spanName);
  }

  /**
	 * Creates a {@link FaasHttpSpan} from a {@link FaasSpan}.
	 * @param builder {@link FaasSpan.FaasSpanBuilder} to use.
	 * @return a {@link FaasHttpSpan} object built from a {@link FaasSpan}.
	 */
  public static FaasHttpSpanBuilder createFaasHttpSpanBuilder(FaasSpan.FaasSpanBuilder builder) {
	  // we accept a builder from Faas since FaasHttp "extends" Faas
    return new FaasHttpSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  @SuppressWarnings("UnnecessaryParentheses")
  public void end() {
    delegate.end();

    // required attributes
    if (!this.status.isSet(AttributeStatus.FAAS_TRIGGER)) {
      logger.warning("Wrong usage - Span missing faas.trigger attribute");
    }
    if (!this.status.isSet(AttributeStatus.HTTP_METHOD)) {
      logger.warning("Wrong usage - Span missing http.method attribute");
    }
    // extra constraints.
    {
      boolean flag =
        (!this.status.isSet(AttributeStatus.HTTP_URL) ) ||
        (!this.status.isSet(AttributeStatus.HTTP_SCHEME) &&!this.status.isSet(AttributeStatus.HTTP_HOST) &&!this.status.isSet(AttributeStatus.HTTP_TARGET) ) ||
        (!this.status.isSet(AttributeStatus.HTTP_SCHEME) &&!this.status.isSet(AttributeStatus.HTTP_SERVER_NAME) &&!this.status.isSet(AttributeStatus.NET_HOST_PORT) &&!this.status.isSet(AttributeStatus.HTTP_TARGET) ) ||
        (!this.status.isSet(AttributeStatus.HTTP_SCHEME) &&!this.status.isSet(AttributeStatus.NET_HOST_NAME) &&!this.status.isSet(AttributeStatus.NET_HOST_PORT) &&!this.status.isSet(AttributeStatus.HTTP_TARGET) ) ;
      if (flag) {
        logger.info("Constraint not respected!");
      }
    }
    // conditional attributes
    if (!this.status.isSet(AttributeStatus.HTTP_STATUS_CODE)) {
      logger.info("WARNING! Missing http.status_code attribute!");
    }
  }


  /**
   * Sets faas.trigger.
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  @Override
  public FaasHttpSemanticConvention setFaasTrigger(String faasTrigger) {
    status.set(AttributeStatus.FAAS_TRIGGER);
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   * @param faasExecution The execution id of the current function execution.
   */
  @Override
  public FaasHttpSemanticConvention setFaasExecution(String faasExecution) {
    status.set(AttributeStatus.FAAS_EXECUTION);
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /**
   * Sets http.server_name.
   * @param httpServerName The primary server name of the matched virtual host. This should be obtained via configuration. If no such configuration can be obtained, this attribute MUST NOT be set ( `net.host.name` should be used instead).
   * <p> http.url is usually not readily available on the server side but would have to be assembled in a cumbersome and sometimes lossy process from other information (see e.g. open-telemetry/opentelemetry-python/pull/148). It is thus preferred to supply the raw data that is available.
   */
  @Override
  public FaasHttpSemanticConvention setHttpServerName(String httpServerName) {
    status.set(AttributeStatus.HTTP_SERVER_NAME);
    delegate.setAttribute("http.server_name", httpServerName);
    return this;
  }

  /**
   * Sets http.route.
   * @param httpRoute The matched route (path template).
   */
  @Override
  public FaasHttpSemanticConvention setHttpRoute(String httpRoute) {
    status.set(AttributeStatus.HTTP_ROUTE);
    delegate.setAttribute("http.route", httpRoute);
    return this;
  }

  /**
   * Sets http.client_ip.
   * @param httpClientIp The IP address of the original client behind all proxies, if known (e.g. from [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
   * <p> This is not necessarily the same as `net.peer.ip`, which would identify the network-level peer, which may be a proxy.
   */
  @Override
  public FaasHttpSemanticConvention setHttpClientIp(String httpClientIp) {
    status.set(AttributeStatus.HTTP_CLIENT_IP);
    delegate.setAttribute("http.client_ip", httpClientIp);
    return this;
  }

  /**
   * Sets http.method.
   * @param httpMethod HTTP request method.
   */
  @Override
  public FaasHttpSemanticConvention setHttpMethod(String httpMethod) {
    status.set(AttributeStatus.HTTP_METHOD);
    delegate.setAttribute("http.method", httpMethod);
    return this;
  }

  /**
   * Sets http.url.
   * @param httpUrl Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`. Usually the fragment is not transmitted over HTTP, but if it is known, it should be included nevertheless.
   */
  @Override
  public FaasHttpSemanticConvention setHttpUrl(String httpUrl) {
    status.set(AttributeStatus.HTTP_URL);
    delegate.setAttribute("http.url", httpUrl);
    return this;
  }

  /**
   * Sets http.target.
   * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
   */
  @Override
  public FaasHttpSemanticConvention setHttpTarget(String httpTarget) {
    status.set(AttributeStatus.HTTP_TARGET);
    delegate.setAttribute("http.target", httpTarget);
    return this;
  }

  /**
   * Sets http.host.
   * @param httpHost The value of the [HTTP host header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not present, this attribute should be the same.
   */
  @Override
  public FaasHttpSemanticConvention setHttpHost(String httpHost) {
    status.set(AttributeStatus.HTTP_HOST);
    delegate.setAttribute("http.host", httpHost);
    return this;
  }

  /**
   * Sets http.scheme.
   * @param httpScheme The URI scheme identifying the used protocol.
   */
  @Override
  public FaasHttpSemanticConvention setHttpScheme(String httpScheme) {
    status.set(AttributeStatus.HTTP_SCHEME);
    delegate.setAttribute("http.scheme", httpScheme);
    return this;
  }

  /**
   * Sets http.status_code.
   * @param httpStatusCode [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  @Override
  public FaasHttpSemanticConvention setHttpStatusCode(long httpStatusCode) {
    status.set(AttributeStatus.HTTP_STATUS_CODE);
    delegate.setAttribute("http.status_code", httpStatusCode);
    return this;
  }

  /**
   * Sets http.status_text.
   * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  @Override
  public FaasHttpSemanticConvention setHttpStatusText(String httpStatusText) {
    status.set(AttributeStatus.HTTP_STATUS_TEXT);
    delegate.setAttribute("http.status_text", httpStatusText);
    return this;
  }

  /**
   * Sets http.flavor.
   * @param httpFlavor Kind of HTTP protocol used.
   * <p> If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor` is `QUIC`, in which case `IP.UDP` is assumed.
   */
  @Override
  public FaasHttpSemanticConvention setHttpFlavor(String httpFlavor) {
    status.set(AttributeStatus.HTTP_FLAVOR);
    delegate.setAttribute("http.flavor", httpFlavor);
    return this;
  }

  /**
   * Sets http.user_agent.
   * @param httpUserAgent Value of the [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
   */
  @Override
  public FaasHttpSemanticConvention setHttpUserAgent(String httpUserAgent) {
    status.set(AttributeStatus.HTTP_USER_AGENT);
    delegate.setAttribute("http.user_agent", httpUserAgent);
    return this;
  }

  /**
   * Sets net.transport.
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public FaasHttpSemanticConvention setNetTransport(String netTransport) {
    status.set(AttributeStatus.NET_TRANSPORT);
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /**
   * Sets net.peer.ip.
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public FaasHttpSemanticConvention setNetPeerIp(String netPeerIp) {
    status.set(AttributeStatus.NET_PEER_IP);
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   * @param netPeerPort Remote port number.
   */
  @Override
  public FaasHttpSemanticConvention setNetPeerPort(long netPeerPort) {
    status.set(AttributeStatus.NET_PEER_PORT);
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public FaasHttpSemanticConvention setNetPeerName(String netPeerName) {
    status.set(AttributeStatus.NET_PEER_NAME);
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public FaasHttpSemanticConvention setNetHostIp(String netHostIp) {
    status.set(AttributeStatus.NET_HOST_IP);
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public FaasHttpSemanticConvention setNetHostPort(long netHostPort) {
    status.set(AttributeStatus.NET_HOST_PORT);
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public FaasHttpSemanticConvention setNetHostName(String netHostName) {
    status.set(AttributeStatus.NET_HOST_NAME);
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }


	/**
	 * Builder class for {@link FaasHttpSpan}.
	 */
	public static class FaasHttpSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected FaasHttpSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasHttpSpanBuilder(Span.Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasHttpSpanBuilder setParent(Span parent){
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public FaasHttpSpanBuilder setParent(SpanContext remoteParent){
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public FaasHttpSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasHttpSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasHttpSpan(this.internalBuilder.startSpan(), status);
    }

    
    /**
     * Sets faas.trigger.
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasHttpSpanBuilder setFaasTrigger(String faasTrigger) {
      status.set(AttributeStatus.FAAS_TRIGGER);
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasHttpSpanBuilder setFaasExecution(String faasExecution) {
      status.set(AttributeStatus.FAAS_EXECUTION);
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }

    /**
     * Sets http.server_name.
     * @param httpServerName The primary server name of the matched virtual host. This should be obtained via configuration. If no such configuration can be obtained, this attribute MUST NOT be set ( `net.host.name` should be used instead).
     * <p> http.url is usually not readily available on the server side but would have to be assembled in a cumbersome and sometimes lossy process from other information (see e.g. open-telemetry/opentelemetry-python/pull/148). It is thus preferred to supply the raw data that is available.
     */
    public FaasHttpSpanBuilder setHttpServerName(String httpServerName) {
      status.set(AttributeStatus.HTTP_SERVER_NAME);
      internalBuilder.setAttribute("http.server_name", httpServerName);
      return this;
    }

    /**
     * Sets http.route.
     * @param httpRoute The matched route (path template).
     */
    public FaasHttpSpanBuilder setHttpRoute(String httpRoute) {
      status.set(AttributeStatus.HTTP_ROUTE);
      internalBuilder.setAttribute("http.route", httpRoute);
      return this;
    }

    /**
     * Sets http.client_ip.
     * @param httpClientIp The IP address of the original client behind all proxies, if known (e.g. from [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
     * <p> This is not necessarily the same as `net.peer.ip`, which would identify the network-level peer, which may be a proxy.
     */
    public FaasHttpSpanBuilder setHttpClientIp(String httpClientIp) {
      status.set(AttributeStatus.HTTP_CLIENT_IP);
      internalBuilder.setAttribute("http.client_ip", httpClientIp);
      return this;
    }

    /**
     * Sets http.method.
     * @param httpMethod HTTP request method.
     */
    public FaasHttpSpanBuilder setHttpMethod(String httpMethod) {
      status.set(AttributeStatus.HTTP_METHOD);
      internalBuilder.setAttribute("http.method", httpMethod);
      return this;
    }

    /**
     * Sets http.url.
     * @param httpUrl Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`. Usually the fragment is not transmitted over HTTP, but if it is known, it should be included nevertheless.
     */
    public FaasHttpSpanBuilder setHttpUrl(String httpUrl) {
      status.set(AttributeStatus.HTTP_URL);
      internalBuilder.setAttribute("http.url", httpUrl);
      return this;
    }

    /**
     * Sets http.target.
     * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
     */
    public FaasHttpSpanBuilder setHttpTarget(String httpTarget) {
      status.set(AttributeStatus.HTTP_TARGET);
      internalBuilder.setAttribute("http.target", httpTarget);
      return this;
    }

    /**
     * Sets http.host.
     * @param httpHost The value of the [HTTP host header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not present, this attribute should be the same.
     */
    public FaasHttpSpanBuilder setHttpHost(String httpHost) {
      status.set(AttributeStatus.HTTP_HOST);
      internalBuilder.setAttribute("http.host", httpHost);
      return this;
    }

    /**
     * Sets http.scheme.
     * @param httpScheme The URI scheme identifying the used protocol.
     */
    public FaasHttpSpanBuilder setHttpScheme(String httpScheme) {
      status.set(AttributeStatus.HTTP_SCHEME);
      internalBuilder.setAttribute("http.scheme", httpScheme);
      return this;
    }

    /**
     * Sets http.status_code.
     * @param httpStatusCode [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
     */
    public FaasHttpSpanBuilder setHttpStatusCode(long httpStatusCode) {
      status.set(AttributeStatus.HTTP_STATUS_CODE);
      internalBuilder.setAttribute("http.status_code", httpStatusCode);
      return this;
    }

    /**
     * Sets http.status_text.
     * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public FaasHttpSpanBuilder setHttpStatusText(String httpStatusText) {
      status.set(AttributeStatus.HTTP_STATUS_TEXT);
      internalBuilder.setAttribute("http.status_text", httpStatusText);
      return this;
    }

    /**
     * Sets http.flavor.
     * @param httpFlavor Kind of HTTP protocol used.
     * <p> If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor` is `QUIC`, in which case `IP.UDP` is assumed.
     */
    public FaasHttpSpanBuilder setHttpFlavor(String httpFlavor) {
      status.set(AttributeStatus.HTTP_FLAVOR);
      internalBuilder.setAttribute("http.flavor", httpFlavor);
      return this;
    }

    /**
     * Sets http.user_agent.
     * @param httpUserAgent Value of the [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
     */
    public FaasHttpSpanBuilder setHttpUserAgent(String httpUserAgent) {
      status.set(AttributeStatus.HTTP_USER_AGENT);
      internalBuilder.setAttribute("http.user_agent", httpUserAgent);
      return this;
    }

    /**
     * Sets net.transport.
     * @param netTransport Transport protocol used. See note below.
     */
    public FaasHttpSpanBuilder setNetTransport(String netTransport) {
      status.set(AttributeStatus.NET_TRANSPORT);
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public FaasHttpSpanBuilder setNetPeerIp(String netPeerIp) {
      status.set(AttributeStatus.NET_PEER_IP);
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     * @param netPeerPort Remote port number.
     */
    public FaasHttpSpanBuilder setNetPeerPort(long netPeerPort) {
      status.set(AttributeStatus.NET_PEER_PORT);
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public FaasHttpSpanBuilder setNetPeerName(String netPeerName) {
      status.set(AttributeStatus.NET_PEER_NAME);
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public FaasHttpSpanBuilder setNetHostIp(String netHostIp) {
      status.set(AttributeStatus.NET_HOST_IP);
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public FaasHttpSpanBuilder setNetHostPort(long netHostPort) {
      status.set(AttributeStatus.NET_HOST_PORT);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     * @param netHostName Local hostname or similar, see note below.
     */
    public FaasHttpSpanBuilder setNetHostName(String netHostName) {
      status.set(AttributeStatus.NET_HOST_NAME);
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }

  }
}