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
 */
public class HttpSpan extends DelegatingSpan implements HttpSemanticConvention {

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
    HTTP_USER_AGENT;

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

  private static final Logger logger = Logger.getLogger(HttpSpan.class.getName());
  final AttributeStatus status;

  protected HttpSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

  /**
   * Entry point to generate a {@link HttpSpan}.
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link HttpSpan} object.
   */
  public static HttpSpanBuilder createHttpSpan(Tracer tracer, String spanName) {
    return new HttpSpanBuilder(tracer, spanName);
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
    // here we check for constraints. We don't have any in Http Span

    // here we check for conditional attributes and we report a warning if missing.
    if (!this.status.isSet(AttributeStatus.HTTP_STATUS_CODE)) {
      logger.warning("Missing http.status_code attribute!");
    }
  }

  /**
   * See http.server convention.
   *
   * @param method HTTP request method.
   */
  public HttpSemanticConvention setMethod(String method) {
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
  public HttpSemanticConvention setUrl(String url) {
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
  public HttpSemanticConvention setTarget(String target) {
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
  public HttpSemanticConvention setHost(String host) {
    status.set(AttributeStatus.HTTP_HOST);
    delegate.setAttribute("http.host", host);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param scheme The URI scheme identifying the used protocol.
   */
  public HttpSemanticConvention setScheme(String scheme) {
    status.set(AttributeStatus.HTTP_SCHEME);
    delegate.setAttribute("http.scheme", scheme);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_code [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  public HttpSemanticConvention setStatusCode(long status_code) {
    // this might be tricky to handle in the template
    status.set(AttributeStatus.HTTP_STATUS_CODE);
    delegate.setAttribute("http.status_code", status_code);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  public HttpSemanticConvention setStatusText(String status_text) {
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
  public HttpSemanticConvention setFlavor(String flavor) {
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
  public HttpSemanticConvention setUserAgent(String user_agent) {
    status.set(AttributeStatus.HTTP_USER_AGENT);
    delegate.setAttribute("http.user_agent", user_agent);
    return this;
  }

  /**
   * Builder class for {@link HttpSpan}.
   */
  public static class HttpSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected HttpSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public HttpSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public HttpSpan start() {
      return new HttpSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * See http.server convention.
     *
     * @param method HTTP request method.
     */
    public HttpSpanBuilder setMethod(String method) {
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
    public HttpSpanBuilder setUrl(String url) {
      status.set(AttributeStatus.HTTP_URL);
      internalBuilder.setAttribute("http.url", url);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param target The full request target as passed in a HTTP request line or equivalent.
     */
    public HttpSpanBuilder setTarget(String target) {
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
    public HttpSpanBuilder setHost(String host) {
      status.set(AttributeStatus.HTTP_HOST);
      internalBuilder.setAttribute("http.host", host);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param scheme The URI scheme identifying the used protocol.
     */
    public HttpSpanBuilder setScheme(String scheme) {
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
    public HttpSpanBuilder setStatusCode(long status_code) {
      status.set(AttributeStatus.HTTP_STATUS_CODE);
      internalBuilder.setAttribute("http.status_code", status_code);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public HttpSpanBuilder setStatusText(String status_text) {
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
    public HttpSpanBuilder setFlavor(String flavor) {
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
    public HttpSpanBuilder setUserAgent(String user_agent) {
      status.set(AttributeStatus.HTTP_USER_AGENT);
      internalBuilder.setAttribute("http.user_agent", user_agent);
      return this;
    }
  }
}
