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

package io.opentelemetry.instrumentation.library.api.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

public class FaasHttpSpan extends DelegatingSpan implements FaasHttpSemanticConvention {

  protected FaasHttpSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link FaasHttpSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasHttpSpan} object.
   */
  public static FaasHttpSpanBuilder createFaasHttpSpan(Tracer tracer, String spanName) {
    return new FaasHttpSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link FaasHttpSpan} from a {@link FaasSpanSpan}.
   *
   * @param builder {@link FaasSpanSpan.FaasSpanSpanBuilder} to use.
   * @return a {@link FaasHttpSpan} object built from a {@link FaasSpanSpan}.
   */
  public static FaasHttpSpanBuilder createFaasHttpSpan(FaasSpanSpan.FaasSpanSpanBuilder builder) {
    // we accept a builder from FaasSpan since FaasHttp "extends" FaasSpan
    return new FaasHttpSpanBuilder(builder.getSpanBuilder());
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
   * Sets faas.trigger.
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  @Override
  public FaasHttpSemanticConvention setFaasTrigger(String faasTrigger) {
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   *
   * @param faasExecution The execution id of the current function execution.
   */
  @Override
  public FaasHttpSemanticConvention setFaasExecution(String faasExecution) {
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /**
   * Sets http.method.
   *
   * @param httpMethod HTTP request method.
   */
  @Override
  public FaasHttpSemanticConvention setHttpMethod(String httpMethod) {
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
  public FaasHttpSemanticConvention setHttpUrl(String httpUrl) {
    delegate.setAttribute("http.url", httpUrl);
    return this;
  }

  /**
   * Sets http.target.
   *
   * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
   */
  @Override
  public FaasHttpSemanticConvention setHttpTarget(String httpTarget) {
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
  public FaasHttpSemanticConvention setHttpHost(String httpHost) {
    delegate.setAttribute("http.host", httpHost);
    return this;
  }

  /**
   * Sets http.scheme.
   *
   * @param httpScheme The URI scheme identifying the used protocol.
   */
  @Override
  public FaasHttpSemanticConvention setHttpScheme(String httpScheme) {
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
  public FaasHttpSemanticConvention setHttpStatusCode(long httpStatusCode) {
    delegate.setAttribute("http.status_code", httpStatusCode);
    return this;
  }

  /**
   * Sets http.status_text.
   *
   * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  @Override
  public FaasHttpSemanticConvention setHttpStatusText(String httpStatusText) {
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
  public FaasHttpSemanticConvention setHttpFlavor(String httpFlavor) {
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
  public FaasHttpSemanticConvention setHttpUserAgent(String httpUserAgent) {
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
  public FaasHttpSemanticConvention setHttpRequestContentLength(long httpRequestContentLength) {
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
  public FaasHttpSemanticConvention setHttpRequestContentLengthUncompressed(
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
  public FaasHttpSemanticConvention setHttpResponseContentLength(long httpResponseContentLength) {
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
  public FaasHttpSemanticConvention setHttpResponseContentLengthUncompressed(
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
  public FaasHttpSemanticConvention setHttpServerName(String httpServerName) {
    delegate.setAttribute("http.server_name", httpServerName);
    return this;
  }

  /**
   * Sets http.route.
   *
   * @param httpRoute The matched route (path template).
   */
  @Override
  public FaasHttpSemanticConvention setHttpRoute(String httpRoute) {
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
  public FaasHttpSemanticConvention setHttpClientIp(String httpClientIp) {
    delegate.setAttribute("http.client_ip", httpClientIp);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below.
   */
  @Override
  public FaasHttpSemanticConvention setNetTransport(String netTransport) {
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
  public FaasHttpSemanticConvention setNetPeerIp(String netPeerIp) {
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number.
   */
  @Override
  public FaasHttpSemanticConvention setNetPeerPort(long netPeerPort) {
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below.
   */
  @Override
  public FaasHttpSemanticConvention setNetPeerName(String netPeerName) {
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
   */
  @Override
  public FaasHttpSemanticConvention setNetHostIp(String netHostIp) {
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port.
   */
  @Override
  public FaasHttpSemanticConvention setNetHostPort(long netHostPort) {
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below.
   */
  @Override
  public FaasHttpSemanticConvention setNetHostName(String netHostName) {
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /** Builder class for {@link FaasHttpSpan}. */
  public static class FaasHttpSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected FaasHttpSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasHttpSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasHttpSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public FaasHttpSpanBuilder setParent(SpanContext remoteParent) {
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
      return new FaasHttpSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasHttpSpanBuilder setFaasTrigger(String faasTrigger) {
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasHttpSpanBuilder setFaasExecution(String faasExecution) {
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }

    /**
     * Sets http.method.
     *
     * @param httpMethod HTTP request method.
     */
    public FaasHttpSpanBuilder setHttpMethod(String httpMethod) {
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
    public FaasHttpSpanBuilder setHttpUrl(String httpUrl) {
      internalBuilder.setAttribute("http.url", httpUrl);
      return this;
    }

    /**
     * Sets http.target.
     *
     * @param httpTarget The full request target as passed in a HTTP request line or equivalent.
     */
    public FaasHttpSpanBuilder setHttpTarget(String httpTarget) {
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
    public FaasHttpSpanBuilder setHttpHost(String httpHost) {
      internalBuilder.setAttribute("http.host", httpHost);
      return this;
    }

    /**
     * Sets http.scheme.
     *
     * @param httpScheme The URI scheme identifying the used protocol.
     */
    public FaasHttpSpanBuilder setHttpScheme(String httpScheme) {
      internalBuilder.setAttribute("http.scheme", httpScheme);
      return this;
    }

    /**
     * Sets http.status_code.
     *
     * @param httpStatusCode [HTTP response status
     *     code](https://tools.ietf.org/html/rfc7231#section-6).
     */
    public FaasHttpSpanBuilder setHttpStatusCode(long httpStatusCode) {
      internalBuilder.setAttribute("http.status_code", httpStatusCode);
      return this;
    }

    /**
     * Sets http.status_text.
     *
     * @param httpStatusText [HTTP reason
     *     phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public FaasHttpSpanBuilder setHttpStatusText(String httpStatusText) {
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
    public FaasHttpSpanBuilder setHttpFlavor(String httpFlavor) {
      internalBuilder.setAttribute("http.flavor", httpFlavor);
      return this;
    }

    /**
     * Sets http.user_agent.
     *
     * @param httpUserAgent Value of the [HTTP
     *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
     */
    public FaasHttpSpanBuilder setHttpUserAgent(String httpUserAgent) {
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
    public FaasHttpSpanBuilder setHttpRequestContentLength(long httpRequestContentLength) {
      internalBuilder.setAttribute("http.request_content_length", httpRequestContentLength);
      return this;
    }

    /**
     * Sets http.request_content_length_uncompressed.
     *
     * @param httpRequestContentLengthUncompressed The size of the uncompressed request payload body
     *     after transport decoding. Not set if transport encoding not used.
     */
    public FaasHttpSpanBuilder setHttpRequestContentLengthUncompressed(
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
    public FaasHttpSpanBuilder setHttpResponseContentLength(long httpResponseContentLength) {
      internalBuilder.setAttribute("http.response_content_length", httpResponseContentLength);
      return this;
    }

    /**
     * Sets http.response_content_length_uncompressed.
     *
     * @param httpResponseContentLengthUncompressed The size of the uncompressed response payload
     *     body after transport decoding. Not set if transport encoding not used.
     */
    public FaasHttpSpanBuilder setHttpResponseContentLengthUncompressed(
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
    public FaasHttpSpanBuilder setHttpServerName(String httpServerName) {
      internalBuilder.setAttribute("http.server_name", httpServerName);
      return this;
    }

    /**
     * Sets http.route.
     *
     * @param httpRoute The matched route (path template).
     */
    public FaasHttpSpanBuilder setHttpRoute(String httpRoute) {
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
    public FaasHttpSpanBuilder setHttpClientIp(String httpClientIp) {
      internalBuilder.setAttribute("http.client_ip", httpClientIp);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below.
     */
    public FaasHttpSpanBuilder setNetTransport(String netTransport) {
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public FaasHttpSpanBuilder setNetPeerIp(String netPeerIp) {
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number.
     */
    public FaasHttpSpanBuilder setNetPeerPort(long netPeerPort) {
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below.
     */
    public FaasHttpSpanBuilder setNetPeerName(String netPeerName) {
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host.
     */
    public FaasHttpSpanBuilder setNetHostIp(String netHostIp) {
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port.
     */
    public FaasHttpSpanBuilder setNetHostPort(long netHostPort) {
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below.
     */
    public FaasHttpSpanBuilder setNetHostName(String netHostName) {
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }
  }
}
