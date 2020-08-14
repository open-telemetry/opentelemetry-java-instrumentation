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

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

public class HttpServerSpan extends DelegatingSpan implements HttpServerSemanticConvention {

  protected HttpServerSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link HttpServerSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link HttpServerSpan} object.
   */
  public static HttpServerSpanBuilder createHttpServerSpan(Tracer tracer, String spanName) {
    return new HttpServerSpanBuilder(tracer, spanName).setKind(Span.Kind.SERVER);
  }

  /**
   * Creates a {@link HttpServerSpan} from a {@link HttpSpan}.
   *
   * @param builder {@link HttpSpan.HttpSpanBuilder} to use.
   * @return a {@link HttpServerSpan} object built from a {@link HttpSpan}.
   */
  public static HttpServerSpanBuilder createHttpServerSpan(HttpSpan.HttpSpanBuilder builder) {
    // we accept a builder from Http since HttpServer "extends" Http
    return new HttpServerSpanBuilder(builder.getSpanBuilder());
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  public void end() {
    delegate.end();
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public HttpServerSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute("net.transport", netTransport);
    return this;
  }

  /**
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public HttpServerSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public HttpServerSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public HttpServerSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public HttpServerSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public HttpServerSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public HttpServerSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /**
   * Sets http.method.
   *
   * @param httpMethod HTTP request method.
   */
  @Override
  public HttpServerSemanticConvention setHttpMethod(String httpMethod) {
    delegate.setAttribute("http.method", httpMethod);
    return this;
  }

  /**
   * Sets http.url.
   *
   * @param httpUrl Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
   *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
   *     included nevertheless.
   */
  @Override
  public HttpServerSemanticConvention setHttpUrl(String httpUrl) {
    delegate.setAttribute("http.url", httpUrl);
    return this;
  }

  /**
   * Sets http.target.
   *
   * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
   */
  @Override
  public HttpServerSemanticConvention setHttpTarget(String httpTarget) {
    delegate.setAttribute("http.target", httpTarget);
    return this;
  }

  /**
   * Sets http.host.
   *
   * @param httpHost The value of the [HTTP host
   *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
   *     present, this attribute should be the same.
   */
  @Override
  public HttpServerSemanticConvention setHttpHost(String httpHost) {
    delegate.setAttribute("http.host", httpHost);
    return this;
  }

  /**
   * Sets http.scheme.
   *
   * @param httpScheme The URI scheme identifying the used protocol.
   */
  @Override
  public HttpServerSemanticConvention setHttpScheme(String httpScheme) {
    delegate.setAttribute("http.scheme", httpScheme);
    return this;
  }

  /**
   * Sets http.status_code.
   *
   * @param httpStatusCode [HTTP response status
   *     code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  @Override
  public HttpServerSemanticConvention setHttpStatusCode(long httpStatusCode) {
    delegate.setAttribute("http.status_code", httpStatusCode);
    return this;
  }

  /**
   * Sets http.status_text.
   *
   * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  @Override
  public HttpServerSemanticConvention setHttpStatusText(String httpStatusText) {
    delegate.setAttribute("http.status_text", httpStatusText);
    return this;
  }

  /**
   * Sets http.flavor.
   *
   * @param httpFlavor Kind of HTTP protocol used.
   *     <p>If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if
   *     `http.flavor` is `QUIC`, in which case `IP.UDP` is assumed.
   */
  @Override
  public HttpServerSemanticConvention setHttpFlavor(String httpFlavor) {
    delegate.setAttribute("http.flavor", httpFlavor);
    return this;
  }

  /**
   * Sets http.user_agent.
   *
   * @param httpUserAgent Value of the [HTTP
   *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
   */
  @Override
  public HttpServerSemanticConvention setHttpUserAgent(String httpUserAgent) {
    delegate.setAttribute("http.user_agent", httpUserAgent);
    return this;
  }

  /**
   * Sets http.request_content_length.
   *
   * @param httpRequestContentLength The size of the request payload body in bytes. This is the
   *     number of bytes transferred excluding headers and is often, but not always, present as the
   *     [Content-Length](https://tools.ietf.org/html/rfc7230#section-3.3.2) header. For requests
   *     using transport encoding, this should be the compressed size.
   */
  @Override
  public HttpServerSemanticConvention setHttpRequestContentLength(long httpRequestContentLength) {
    delegate.setAttribute("http.request_content_length", httpRequestContentLength);
    return this;
  }

  /**
   * Sets http.request_content_length_uncompressed.
   *
   * @param httpRequestContentLengthUncompressed The size of the uncompressed request payload body
   *     after transport decoding. Not set if transport encoding not used.
   */
  @Override
  public HttpServerSemanticConvention setHttpRequestContentLengthUncompressed(
      long httpRequestContentLengthUncompressed) {
    delegate.setAttribute(
        "http.request_content_length_uncompressed", httpRequestContentLengthUncompressed);
    return this;
  }

  /**
   * Sets http.response_content_length.
   *
   * @param httpResponseContentLength The size of the response payload body in bytes. This is the
   *     number of bytes transferred excluding headers and is often, but not always, present as the
   *     [Content-Length](https://tools.ietf.org/html/rfc7230#section-3.3.2) header. For requests
   *     using transport encoding, this should be the compressed size.
   */
  @Override
  public HttpServerSemanticConvention setHttpResponseContentLength(long httpResponseContentLength) {
    delegate.setAttribute("http.response_content_length", httpResponseContentLength);
    return this;
  }

  /**
   * Sets http.response_content_length_uncompressed.
   *
   * @param httpResponseContentLengthUncompressed The size of the uncompressed response payload body
   *     after transport decoding. Not set if transport encoding not used.
   */
  @Override
  public HttpServerSemanticConvention setHttpResponseContentLengthUncompressed(
      long httpResponseContentLengthUncompressed) {
    delegate.setAttribute(
        "http.response_content_length_uncompressed", httpResponseContentLengthUncompressed);
    return this;
  }

  /**
   * Sets http.server_name.
   *
   * @param httpServerName The primary server name of the matched virtual host. This should be
   *     obtained via configuration. If no such configuration can be obtained, this attribute MUST
   *     NOT be set ( `net.host.name` should be used instead).
   *     <p>http.url is usually not readily available on the server side but would have to be
   *     assembled in a cumbersome and sometimes lossy process from other information (see e.g.
   *     open-telemetry/opentelemetry-python/pull/148). It is thus preferred to supply the raw data
   *     that is available.
   */
  @Override
  public HttpServerSemanticConvention setHttpServerName(String httpServerName) {
    delegate.setAttribute("http.server_name", httpServerName);
    return this;
  }

  /**
   * Sets http.route.
   *
   * @param httpRoute The matched route (path template).
   */
  @Override
  public HttpServerSemanticConvention setHttpRoute(String httpRoute) {
    delegate.setAttribute("http.route", httpRoute);
    return this;
  }

  /**
   * Sets http.client_ip.
   *
   * @param httpClientIp The IP address of the original client behind all proxies, if known (e.g.
   *     from
   *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
   *     <p>This is not necessarily the same as `net.peer.ip`, which would identify the
   *     network-level peer, which may be a proxy.
   */
  @Override
  public HttpServerSemanticConvention setHttpClientIp(String httpClientIp) {
    delegate.setAttribute("http.client_ip", httpClientIp);
    return this;
  }

  /** Builder class for {@link HttpServerSpan}. */
  public static class HttpServerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected HttpServerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public HttpServerSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public HttpServerSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public HttpServerSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public HttpServerSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public HttpServerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new HttpServerSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public HttpServerSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public HttpServerSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public HttpServerSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public HttpServerSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public HttpServerSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public HttpServerSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public HttpServerSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }

    /**
     * Sets http.method.
     *
     * @param httpMethod HTTP request method.
     */
    public HttpServerSpanBuilder setHttpMethod(String httpMethod) {
      internalBuilder.setAttribute("http.method", httpMethod);
      return this;
    }

    /**
     * Sets http.url.
     *
     * @param httpUrl Full HTTP request URL in the form
     *     `scheme://host[:port]/path?query[#fragment]`. Usually the fragment is not transmitted
     *     over HTTP, but if it is known, it should be included nevertheless.
     */
    public HttpServerSpanBuilder setHttpUrl(String httpUrl) {
      internalBuilder.setAttribute("http.url", httpUrl);
      return this;
    }

    /**
     * Sets http.target.
     *
     * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
     */
    public HttpServerSpanBuilder setHttpTarget(String httpTarget) {
      internalBuilder.setAttribute("http.target", httpTarget);
      return this;
    }

    /**
     * Sets http.host.
     *
     * @param httpHost The value of the [HTTP host
     *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
     *     present, this attribute should be the same.
     */
    public HttpServerSpanBuilder setHttpHost(String httpHost) {
      internalBuilder.setAttribute("http.host", httpHost);
      return this;
    }

    /**
     * Sets http.scheme.
     *
     * @param httpScheme The URI scheme identifying the used protocol.
     */
    public HttpServerSpanBuilder setHttpScheme(String httpScheme) {
      internalBuilder.setAttribute("http.scheme", httpScheme);
      return this;
    }

    /**
     * Sets http.status_code.
     *
     * @param httpStatusCode [HTTP response status
     *     code](https://tools.ietf.org/html/rfc7231#section-6).
     */
    public HttpServerSpanBuilder setHttpStatusCode(long httpStatusCode) {
      internalBuilder.setAttribute("http.status_code", httpStatusCode);
      return this;
    }

    /**
     * Sets http.status_text.
     *
     * @param httpStatusText [HTTP reason
     *     phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public HttpServerSpanBuilder setHttpStatusText(String httpStatusText) {
      internalBuilder.setAttribute("http.status_text", httpStatusText);
      return this;
    }

    /**
     * Sets http.flavor.
     *
     * @param httpFlavor Kind of HTTP protocol used.
     *     <p>If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if
     *     `http.flavor` is `QUIC`, in which case `IP.UDP` is assumed.
     */
    public HttpServerSpanBuilder setHttpFlavor(String httpFlavor) {
      internalBuilder.setAttribute("http.flavor", httpFlavor);
      return this;
    }

    /**
     * Sets http.user_agent.
     *
     * @param httpUserAgent Value of the [HTTP
     *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
     */
    public HttpServerSpanBuilder setHttpUserAgent(String httpUserAgent) {
      internalBuilder.setAttribute("http.user_agent", httpUserAgent);
      return this;
    }

    /**
     * Sets http.request_content_length.
     *
     * @param httpRequestContentLength The size of the request payload body in bytes. This is the
     *     number of bytes transferred excluding headers and is often, but not always, present as
     *     the [Content-Length](https://tools.ietf.org/html/rfc7230#section-3.3.2) header. For
     *     requests using transport encoding, this should be the compressed size.
     */
    public HttpServerSpanBuilder setHttpRequestContentLength(long httpRequestContentLength) {
      internalBuilder.setAttribute("http.request_content_length", httpRequestContentLength);
      return this;
    }

    /**
     * Sets http.request_content_length_uncompressed.
     *
     * @param httpRequestContentLengthUncompressed The size of the uncompressed request payload body
     *     after transport decoding. Not set if transport encoding not used.
     */
    public HttpServerSpanBuilder setHttpRequestContentLengthUncompressed(
        long httpRequestContentLengthUncompressed) {
      internalBuilder.setAttribute(
          "http.request_content_length_uncompressed", httpRequestContentLengthUncompressed);
      return this;
    }

    /**
     * Sets http.response_content_length.
     *
     * @param httpResponseContentLength The size of the response payload body in bytes. This is the
     *     number of bytes transferred excluding headers and is often, but not always, present as
     *     the [Content-Length](https://tools.ietf.org/html/rfc7230#section-3.3.2) header. For
     *     requests using transport encoding, this should be the compressed size.
     */
    public HttpServerSpanBuilder setHttpResponseContentLength(long httpResponseContentLength) {
      internalBuilder.setAttribute("http.response_content_length", httpResponseContentLength);
      return this;
    }

    /**
     * Sets http.response_content_length_uncompressed.
     *
     * @param httpResponseContentLengthUncompressed The size of the uncompressed response payload
     *     body after transport decoding. Not set if transport encoding not used.
     */
    public HttpServerSpanBuilder setHttpResponseContentLengthUncompressed(
        long httpResponseContentLengthUncompressed) {
      internalBuilder.setAttribute(
          "http.response_content_length_uncompressed", httpResponseContentLengthUncompressed);
      return this;
    }

    /**
     * Sets http.server_name.
     *
     * @param httpServerName The primary server name of the matched virtual host. This should be
     *     obtained via configuration. If no such configuration can be obtained, this attribute MUST
     *     NOT be set ( `net.host.name` should be used instead).
     *     <p>http.url is usually not readily available on the server side but would have to be
     *     assembled in a cumbersome and sometimes lossy process from other information (see e.g.
     *     open-telemetry/opentelemetry-python/pull/148). It is thus preferred to supply the raw
     *     data that is available.
     */
    public HttpServerSpanBuilder setHttpServerName(String httpServerName) {
      internalBuilder.setAttribute("http.server_name", httpServerName);
      return this;
    }

    /**
     * Sets http.route.
     *
     * @param httpRoute The matched route (path template).
     */
    public HttpServerSpanBuilder setHttpRoute(String httpRoute) {
      internalBuilder.setAttribute("http.route", httpRoute);
      return this;
    }

    /**
     * Sets http.client_ip.
     *
     * @param httpClientIp The IP address of the original client behind all proxies, if known (e.g.
     *     from
     *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
     *     <p>This is not necessarily the same as `net.peer.ip`, which would identify the
     *     network-level peer, which may be a proxy.
     */
    public HttpServerSpanBuilder setHttpClientIp(String httpClientIp) {
      internalBuilder.setAttribute("http.client_ip", httpClientIp);
      return this;
    }
  }
}
