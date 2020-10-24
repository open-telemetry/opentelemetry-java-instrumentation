/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.context.Context;
import static io.opentelemetry.trace.attributes.SemanticAttributes.*;

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class HttpClientSpan extends DelegatingSpan implements HttpClientSemanticConvention {

  protected HttpClientSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link HttpClientSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link HttpClientSpan} object.
   */
  public static HttpClientSpanBuilder createHttpClientSpan(Tracer tracer, String spanName) {
    return new HttpClientSpanBuilder(tracer, spanName).setKind(Span.Kind.CLIENT);
  }

  /**
   * Creates a {@link HttpClientSpan} from a {@link HttpSpan}.
   *
   * @param builder {@link HttpSpan.HttpSpanBuilder} to use.
   * @return a {@link HttpClientSpan} object built from a {@link HttpSpan}.
   */
  public static HttpClientSpanBuilder createHttpClientSpan(HttpSpan.HttpSpanBuilder builder) {
    // we accept a builder from Http since HttpClient "extends" Http
    return new HttpClientSpanBuilder(builder.getSpanBuilder());
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
  public HttpClientSemanticConvention setNetTransport(String netTransport) {
    delegate.setAttribute(NET_TRANSPORT, netTransport);
    return this;
  }

  /**
   * Sets net.peer.ip.
   *
   * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
   *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
   */
  @Override
  public HttpClientSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute(NET_PEER_IP, netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public HttpClientSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute(NET_PEER_PORT, netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public HttpClientSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute(NET_PEER_NAME, netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public HttpClientSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute(NET_HOST_IP, netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public HttpClientSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute(NET_HOST_PORT, netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public HttpClientSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute(NET_HOST_NAME, netHostName);
    return this;
  }

  /**
   * Sets http.method.
   *
   * @param httpMethod HTTP request method.
   */
  @Override
  public HttpClientSemanticConvention setHttpMethod(String httpMethod) {
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
  public HttpClientSemanticConvention setHttpUrl(String httpUrl) {
    delegate.setAttribute("http.url", httpUrl);
    return this;
  }

  /**
   * Sets http.target.
   *
   * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
   */
  @Override
  public HttpClientSemanticConvention setHttpTarget(String httpTarget) {
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
  public HttpClientSemanticConvention setHttpHost(String httpHost) {
    delegate.setAttribute("http.host", httpHost);
    return this;
  }

  /**
   * Sets http.scheme.
   *
   * @param httpScheme The URI scheme identifying the used protocol.
   */
  @Override
  public HttpClientSemanticConvention setHttpScheme(String httpScheme) {
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
  public HttpClientSemanticConvention setHttpStatusCode(long httpStatusCode) {
    delegate.setAttribute("http.status_code", httpStatusCode);
    return this;
  }

  /**
   * Sets http.status_text.
   *
   * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  @Override
  public HttpClientSemanticConvention setHttpStatusText(String httpStatusText) {
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
  public HttpClientSemanticConvention setHttpFlavor(String httpFlavor) {
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
  public HttpClientSemanticConvention setHttpUserAgent(String httpUserAgent) {
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
  public HttpClientSemanticConvention setHttpRequestContentLength(long httpRequestContentLength) {
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
  public HttpClientSemanticConvention setHttpRequestContentLengthUncompressed(
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
  public HttpClientSemanticConvention setHttpResponseContentLength(long httpResponseContentLength) {
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
  public HttpClientSemanticConvention setHttpResponseContentLengthUncompressed(
      long httpResponseContentLengthUncompressed) {
    delegate.setAttribute(
        "http.response_content_length_uncompressed", httpResponseContentLengthUncompressed);
    return this;
  }

  /** Builder class for {@link HttpClientSpan}. */
  public static class HttpClientSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected HttpClientSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public HttpClientSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public HttpClientSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public HttpClientSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public HttpClientSpan start() {
      // check for sampling relevant field here, but there are none.
      return new HttpClientSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public HttpClientSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute(NET_TRANSPORT, netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public HttpClientSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute(NET_PEER_IP, netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public HttpClientSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute(NET_PEER_PORT, netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public HttpClientSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute(NET_PEER_NAME, netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public HttpClientSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute(NET_HOST_IP, netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public HttpClientSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute(NET_HOST_PORT, netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public HttpClientSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute(NET_HOST_NAME, netHostName);
      return this;
    }

    /**
     * Sets http.method.
     *
     * @param httpMethod HTTP request method.
     */
    public HttpClientSpanBuilder setHttpMethod(String httpMethod) {
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
    public HttpClientSpanBuilder setHttpUrl(String httpUrl) {
      internalBuilder.setAttribute("http.url", httpUrl);
      return this;
    }

    /**
     * Sets http.target.
     *
     * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
     */
    public HttpClientSpanBuilder setHttpTarget(String httpTarget) {
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
    public HttpClientSpanBuilder setHttpHost(String httpHost) {
      internalBuilder.setAttribute("http.host", httpHost);
      return this;
    }

    /**
     * Sets http.scheme.
     *
     * @param httpScheme The URI scheme identifying the used protocol.
     */
    public HttpClientSpanBuilder setHttpScheme(String httpScheme) {
      internalBuilder.setAttribute("http.scheme", httpScheme);
      return this;
    }

    /**
     * Sets http.status_code.
     *
     * @param httpStatusCode [HTTP response status
     *     code](https://tools.ietf.org/html/rfc7231#section-6).
     */
    public HttpClientSpanBuilder setHttpStatusCode(long httpStatusCode) {
      internalBuilder.setAttribute("http.status_code", httpStatusCode);
      return this;
    }

    /**
     * Sets http.status_text.
     *
     * @param httpStatusText [HTTP reason
     *     phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public HttpClientSpanBuilder setHttpStatusText(String httpStatusText) {
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
    public HttpClientSpanBuilder setHttpFlavor(String httpFlavor) {
      internalBuilder.setAttribute("http.flavor", httpFlavor);
      return this;
    }

    /**
     * Sets http.user_agent.
     *
     * @param httpUserAgent Value of the [HTTP
     *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
     */
    public HttpClientSpanBuilder setHttpUserAgent(String httpUserAgent) {
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
    public HttpClientSpanBuilder setHttpRequestContentLength(long httpRequestContentLength) {
      internalBuilder.setAttribute("http.request_content_length", httpRequestContentLength);
      return this;
    }

    /**
     * Sets http.request_content_length_uncompressed.
     *
     * @param httpRequestContentLengthUncompressed The size of the uncompressed request payload body
     *     after transport decoding. Not set if transport encoding not used.
     */
    public HttpClientSpanBuilder setHttpRequestContentLengthUncompressed(
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
    public HttpClientSpanBuilder setHttpResponseContentLength(long httpResponseContentLength) {
      internalBuilder.setAttribute("http.response_content_length", httpResponseContentLength);
      return this;
    }

    /**
     * Sets http.response_content_length_uncompressed.
     *
     * @param httpResponseContentLengthUncompressed The size of the uncompressed response payload
     *     body after transport decoding. Not set if transport encoding not used.
     */
    public HttpClientSpanBuilder setHttpResponseContentLengthUncompressed(
        long httpResponseContentLengthUncompressed) {
      internalBuilder.setAttribute(
          "http.response_content_length_uncompressed", httpResponseContentLengthUncompressed);
      return this;
    }
  }
}
