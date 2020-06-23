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
package io.opentelemetry.auto.typed.http.delegate;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Set;
import java.util.logging.Logger;

/**
 * <b>Required attributes:</b>
 *
 * <ul>
 *   <li>http.method: Full HTTP request URL in the form
 *       `scheme://host[:port]/path?query[#fragment]`. Usually the fragment is not transmitted over
 *       HTTP, but if it is known, it should be included nevertheless.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 *   <li>http.status_code: If and only if one was received/sent.
 * </ul>
 *
 * <b>Additional constraints</b>
 *
 * <p>At least one of the following must be set:
 *
 * <ul>
 *   <li>http.url
 *   <li>http.scheme, http.host, http.target
 *   <li>http.scheme, net.peer.name, net.peer.port, http.target
 *   <li>http.scheme, net.peer.ip, net.peer.port, http.target
 * </ul>
 */
public class HttpServerSpan extends DelegatingSpan implements HttpServerSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    HTTP_METHOD,
    HTTP_URL,
    HTTP_TARGET,
    HTTP_HOST,
    HTTP_SCHEME,
    HTTP_STATUS_CODE,
    HTTP_STATUS_TEXT,
    HTTP_FLAVOR,
    HTTP_USER_AGENT,
    HTTP_SERVER_NAME,
    HTTP_ROUTE,
    HTTP_CLIENT_IP,
    NET_HOST_PORT,
    NET_HOST_NAME;

    private long flag;

    AttributeStatus() {
      this.flag = 1 << this.ordinal();
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

  private static final Logger logger = Logger.getLogger(HttpServerSpan.class.getName());
  public final AttributeStatus status;

  protected HttpServerSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

	/**
	 * Entry point to generate a {@link HttpServerSpan}.
	 * @param tracer Tracer to use
	 * @param spanName Name for the {@link Span}
	 * @return a {@link HttpServerSpan} object.
	 */
  public static HttpServerSpanBuilder createHttpServerSpan(Tracer tracer, String spanName) {
	  // Must be a Kind.Server span
    return new HttpServerSpanBuilder(tracer, spanName).setKind(Span.Kind.SERVER);
  }

	/**
	 * Creates a {@link HttpServerSpan} from a {@link HttpSpan}.
	 * @param builder {@link HttpSpan.HttpSpanBuilder} to use.
	 * @return a {@link HttpServerSpan} object built from a {@link HttpSpan}.
	 */
  public static HttpServerSpanBuilder createHttpServerSpan(HttpSpan.HttpSpanBuilder builder) {
	  // we accept a builder from Http since Http Server "extends" Http
    return new HttpServerSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
  }

  /** @return the Span used internally */
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  public void end() {
    delegate.end();
    // required attributes
    if (!this.status.isSet(AttributeStatus.HTTP_METHOD)) {
      logger.warning("Wrong usage - Span missing http.method attribute");
    }
    // here we check for extra constraints. HttpServer has a single condition with four different
    // cases.
    boolean flag =
        (!this.status.isSet(AttributeStatus.HTTP_URL))
            || (!this.status.isSet(AttributeStatus.HTTP_SCHEME)
                && !this.status.isSet(AttributeStatus.HTTP_HOST)
                && !this.status.isSet(AttributeStatus.HTTP_TARGET))
            || (!this.status.isSet(AttributeStatus.HTTP_SCHEME)
                && !this.status.isSet(AttributeStatus.HTTP_SERVER_NAME)
                && !this.status.isSet(AttributeStatus.NET_HOST_PORT)
                && !this.status.isSet(AttributeStatus.HTTP_TARGET))
            || (!this.status.isSet(AttributeStatus.HTTP_SCHEME)
                && !this.status.isSet(AttributeStatus.NET_HOST_NAME)
                && !this.status.isSet(AttributeStatus.NET_HOST_PORT)
                && !this.status.isSet(AttributeStatus.HTTP_TARGET));
    if (flag) {
      logger.info("Constraint not respected!");
    }

    // here we check for conditional attributes and we report a warning if missing.
    if (!this.status.isSet(AttributeStatus.HTTP_STATUS_CODE)) {
      logger.warning("Missing http.status_code attribute!");
    }
  }

  /** @param netHostPort Like `net.peer.port` but for the host port. */
  public HttpServerSemanticConvention setNetHostPort(long netHostPort) {
    status.set(AttributeStatus.NET_HOST_PORT);
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /** @param netHostName Local hostname or similar, see note below. */
  public HttpServerSemanticConvention setNetHostName(String netHostName) {
    status.set(AttributeStatus.NET_HOST_NAME);
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /**
   * http.url is usually not readily available on the server side but would have to be assembled in
   * a cumbersome and sometimes lossy process from other information (see e.g.
   * open-telemetry/opentelemetry-python/pull/148). It is thus preferred to supply the raw data that
   * is available.
   *
   * @param server_name The primary server name of the matched virtual host. This should be obtained
   *     via configuration. If no such configuration can be obtained, this attribute MUST NOT be set
   *     ( `net.host.name` should be used instead).
   */
  public HttpServerSemanticConvention setServerName(String server_name) {
    status.set(AttributeStatus.HTTP_SERVER_NAME);
    delegate.setAttribute("http.server_name", server_name);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param route The matched route (path template).
   */
  public HttpServerSemanticConvention setRoute(String route) {
    status.set(AttributeStatus.HTTP_ROUTE);
    delegate.setAttribute("http.route", route);
    return this;
  }

  /**
   * This is not necessarily the same as `net.peer.ip`, which would identify the network-level peer,
   * which may be a proxy.
   *
   * @param client_ip The IP address of the original client behind all proxies, if known (e.g. from
   *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
   */
  public HttpServerSemanticConvention setClientIp(String client_ip) {
    status.set(AttributeStatus.HTTP_CLIENT_IP);
    delegate.setAttribute("http.client_ip", client_ip);
    return this;
  }

  // All the remaining methods are duplicated from Http.
  // This will be automatically generated code so duplication is fine.

  /**
   * See http.server convention.
   *
   * @param method HTTP request method.
   */
  public HttpServerSemanticConvention setMethod(String method) {
    status.set(AttributeStatus.HTTP_METHOD);
    delegate.setAttribute("http.method", method);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param url Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
   *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
   *     included nevertheless.
   */
  public HttpServerSemanticConvention setUrl(String url) {
    status.set(AttributeStatus.HTTP_URL);
    delegate.setAttribute("http.url", url);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param target The full request target as passed in a HTTP request line or equivalent.
   * @since Semantic Convention 1.
   */
  public HttpServerSemanticConvention setTarget(String target) {
    status.set(AttributeStatus.HTTP_TARGET);
    delegate.setAttribute("http.target", target);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param host The value of the [HTTP host
   *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
   *     present, this attribute should be the same.
   */
  public HttpServerSemanticConvention setHost(String host) {
    status.set(AttributeStatus.HTTP_HOST);
    delegate.setAttribute("http.host", host);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param scheme The URI scheme identifying the used protocol.
   */
  public HttpServerSemanticConvention setScheme(String scheme) {
    status.set(AttributeStatus.HTTP_SCHEME);
    delegate.setAttribute("http.scheme", scheme);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_code [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  public HttpServerSemanticConvention setStatusCode(long status_code) {
    status.set(AttributeStatus.HTTP_STATUS_CODE);
    delegate.setAttribute("http.status_code", status_code);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  public HttpServerSemanticConvention setStatusText(String status_text) {
    status.set(AttributeStatus.HTTP_STATUS_TEXT);
    delegate.setAttribute("http.status_text", status_text);
    return this;
  }

  /**
   * If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor`
   * is `QUIC`, in which case `IP.UDP` is assumed.
   *
   * @param flavor Kind of HTTP protocol used.
   */
  public HttpServerSemanticConvention setFlavor(String flavor) {
    status.set(AttributeStatus.HTTP_FLAVOR);
    delegate.setAttribute("http.flavor", flavor);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param user_agent Value of the [HTTP
   *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
   */
  public HttpServerSemanticConvention setUserAgent(String user_agent) {
    status.set(AttributeStatus.HTTP_USER_AGENT);
    delegate.setAttribute("http.user_agent", user_agent);
    return this;
  }

	/**
	 * Builder class for {@link HttpServerSpan}.
	 */
	public static class HttpServerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected HttpServerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public HttpServerSpanBuilder(Span.Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public HttpServerSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public HttpServerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new HttpServerSpan(this.internalBuilder.startSpan(), status);
    }

    /** @param netHostPort Like `net.peer.port` but for the host port. */
    public HttpServerSpanBuilder setNetHostPort(long netHostPort) {
      status.set(AttributeStatus.NET_HOST_PORT);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /** @param netHostName Local hostname or similar, see note below. */
    public HttpServerSpanBuilder setNetHostName(String netHostName) {
      status.set(AttributeStatus.NET_HOST_NAME);
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }

    /**
     * http.url is usually not readily available on the server side but would have to be assembled
     * in a cumbersome and sometimes lossy process from other information (see e.g.
     * open-telemetry/opentelemetry-python/pull/148). It is thus preferred to supply the raw data
     * that is available.
     *
     * @param server_name The primary server name of the matched virtual host. This should be
     *     obtained via configuration. If no such configuration can be obtained, this attribute MUST
     *     NOT be set ( `net.host.name` should be used instead).
     */
    public HttpServerSpanBuilder setServerName(String server_name) {
      status.set(AttributeStatus.HTTP_SERVER_NAME);
      internalBuilder.setAttribute("http.server_name", server_name);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param route The matched route (path template).
     */
    public HttpServerSpanBuilder setRoute(String route) {
      status.set(AttributeStatus.HTTP_ROUTE);
      internalBuilder.setAttribute("http.route", route);
      return this;
    }

    /**
     * This is not necessarily the same as `net.peer.ip`, which would identify the network-level
     * peer, which may be a proxy.
     *
     * @param client_ip The IP address of the original client behind all proxies, if known (e.g.
     *     from
     *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
     */
    public HttpServerSpanBuilder setClientIp(String client_ip) {
      status.set(AttributeStatus.HTTP_CLIENT_IP);
      internalBuilder.setAttribute("http.client_ip", client_ip);
      return this;
    }

    // All the remaining methods are duplicated from Http.

    /**
     * See http.server convention.
     *
     * @param method HTTP request method.
     */
    public HttpServerSpanBuilder setMethod(String method) {
      status.set(AttributeStatus.HTTP_METHOD);
      internalBuilder.setAttribute("http.method", method);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param url Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
     *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
     *     included nevertheless.
     */
    public HttpServerSpanBuilder setUrl(String url) {
      status.set(AttributeStatus.HTTP_URL);
      internalBuilder.setAttribute("http.url", url);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param target The full request target as passed in a HTTP request line or equivalent.
     */
    public HttpServerSpanBuilder setTarget(String target) {
      status.set(AttributeStatus.HTTP_TARGET);
      internalBuilder.setAttribute("http.target", target);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param host The value of the [HTTP host
     *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
     *     present, this attribute should be the same.
     */
    public HttpServerSpanBuilder setHost(String host) {
      status.set(AttributeStatus.HTTP_HOST);
      internalBuilder.setAttribute("http.host", host);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param scheme The URI scheme identifying the used protocol.
     */
    public HttpServerSpanBuilder setScheme(String scheme) {
      status.set(AttributeStatus.HTTP_SCHEME);
      internalBuilder.setAttribute("http.scheme", scheme);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param status_code [HTTP response status
     *     code](https://tools.ietf.org/html/rfc7231#section-6).
     */
    public HttpServerSpanBuilder setStatusCode(long status_code) {
      status.set(AttributeStatus.HTTP_STATUS_CODE);
      internalBuilder.setAttribute("http.status_code", status_code);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public HttpServerSpanBuilder setStatusText(String status_text) {
      status.set(AttributeStatus.HTTP_STATUS_TEXT);
      internalBuilder.setAttribute("http.status_text", status_text);
      return this;
    }

    /**
     * If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor`
     * is `QUIC`, in which case `IP.UDP` is assumed.
     *
     * @param flavor Kind of HTTP protocol used.
     */
    public HttpServerSpanBuilder setFlavor(String flavor) {
      status.set(AttributeStatus.HTTP_FLAVOR);
      internalBuilder.setAttribute("http.flavor", flavor);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param user_agent Value of the [HTTP
     *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
     */
    public HttpServerSpanBuilder setUserAgent(String user_agent) {
      status.set(AttributeStatus.HTTP_USER_AGENT);
      internalBuilder.setAttribute("http.user_agent", user_agent);
      return this;
    }
  }
}
