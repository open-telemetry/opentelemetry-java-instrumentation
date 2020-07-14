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
 *   <li>http.method: HTTP request method.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 *   <li>http.status_code: [HTTP response status
 *       code](https://tools.ietf.org/html/rfc7231#section-6).
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
public class HttpClientSpan extends DelegatingSpan implements HttpClientSemanticConvention {

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
  private static final Logger logger = Logger.getLogger(HttpClientSpan.class.getName());

  public final AttributeStatus status;

  protected HttpClientSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

  /**
   * Entry point to generate a {@link HttpClientSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link HttpClientSpan} object.
   */
  public static HttpClientSpanBuilder createHttpClientSpan(Tracer tracer, String spanName) {
    return new HttpClientSpanBuilder(tracer, spanName).setKind(Kind.CLIENT);
  }

  /**
   * Creates a {@link HttpClientSpan} from a {@link HttpSpan}.
   *
   * @param builder {@link HttpSpan.HttpSpanBuilder} to use.
   * @return a {@link HttpClientSpan} object built from a {@link HttpSpan}.
   */
  public static HttpClientSpanBuilder createHttpClientSpan(HttpSpan.HttpSpanBuilder builder) {
    // we accept a builder from Http since HttpClient "extends" Http
    return new HttpClientSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
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
    if (!this.status.isSet(AttributeStatus.HTTP_METHOD)) {
      logger.warning("Wrong usage - Span missing http.method attribute");
    }
    // extra constraints.
    {
      boolean flag =
          (!this.status.isSet(AttributeStatus.HTTP_URL))
              || (!this.status.isSet(AttributeStatus.HTTP_SCHEME)
                  && !this.status.isSet(AttributeStatus.HTTP_HOST)
                  && !this.status.isSet(AttributeStatus.HTTP_TARGET))
              || (!this.status.isSet(AttributeStatus.HTTP_SCHEME)
                  && !this.status.isSet(AttributeStatus.NET_PEER_NAME)
                  && !this.status.isSet(AttributeStatus.NET_PEER_PORT)
                  && !this.status.isSet(AttributeStatus.HTTP_TARGET))
              || (!this.status.isSet(AttributeStatus.HTTP_SCHEME)
                  && !this.status.isSet(AttributeStatus.NET_PEER_IP)
                  && !this.status.isSet(AttributeStatus.NET_PEER_PORT)
                  && !this.status.isSet(AttributeStatus.HTTP_TARGET));
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
   * Sets http.method.
   *
   * @param httpMethod HTTP request method..
   */
  @Override
  public HttpClientSemanticConvention setHttpMethod(String httpMethod) {
    status.set(AttributeStatus.HTTP_METHOD);
    delegate.setAttribute("http.method", httpMethod);
    return this;
  }

  /**
   * Sets http.url.
   *
   * @param httpUrl Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
   *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
   *     included nevertheless..
   */
  @Override
  public HttpClientSemanticConvention setHttpUrl(String httpUrl) {
    status.set(AttributeStatus.HTTP_URL);
    delegate.setAttribute("http.url", httpUrl);
    return this;
  }

  /**
   * Sets http.target.
   *
   * @param httpTarget The full request target as passed in a HTTP request line or equivalent..
   */
  @Override
  public HttpClientSemanticConvention setHttpTarget(String httpTarget) {
    status.set(AttributeStatus.HTTP_TARGET);
    delegate.setAttribute("http.target", httpTarget);
    return this;
  }

  /**
   * Sets http.host.
   *
   * @param httpHost The value of the [HTTP host
   *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
   *     present, this attribute should be the same..
   */
  @Override
  public HttpClientSemanticConvention setHttpHost(String httpHost) {
    status.set(AttributeStatus.HTTP_HOST);
    delegate.setAttribute("http.host", httpHost);
    return this;
  }

  /**
   * Sets http.scheme.
   *
   * @param httpScheme The URI scheme identifying the used protocol..
   */
  @Override
  public HttpClientSemanticConvention setHttpScheme(String httpScheme) {
    status.set(AttributeStatus.HTTP_SCHEME);
    delegate.setAttribute("http.scheme", httpScheme);
    return this;
  }

  /**
   * Sets http.status_code.
   *
   * @param httpStatusCode [HTTP response status
   *     code](https://tools.ietf.org/html/rfc7231#section-6)..
   */
  @Override
  public HttpClientSemanticConvention setHttpStatusCode(long httpStatusCode) {
    status.set(AttributeStatus.HTTP_STATUS_CODE);
    delegate.setAttribute("http.status_code", httpStatusCode);
    return this;
  }

  /**
   * Sets http.status_text.
   *
   * @param httpStatusText [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2)..
   */
  @Override
  public HttpClientSemanticConvention setHttpStatusText(String httpStatusText) {
    status.set(AttributeStatus.HTTP_STATUS_TEXT);
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
    status.set(AttributeStatus.HTTP_FLAVOR);
    delegate.setAttribute("http.flavor", httpFlavor);
    return this;
  }

  /**
   * Sets http.user_agent.
   *
   * @param httpUserAgent Value of the [HTTP
   *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client..
   */
  @Override
  public HttpClientSemanticConvention setHttpUserAgent(String httpUserAgent) {
    status.set(AttributeStatus.HTTP_USER_AGENT);
    delegate.setAttribute("http.user_agent", httpUserAgent);
    return this;
  }

  /**
   * Sets net.transport.
   *
   * @param netTransport Transport protocol used. See note below..
   */
  @Override
  public HttpClientSemanticConvention setNetTransport(String netTransport) {
    status.set(AttributeStatus.NET_TRANSPORT);
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
  public HttpClientSemanticConvention setNetPeerIp(String netPeerIp) {
    status.set(AttributeStatus.NET_PEER_IP);
    delegate.setAttribute("net.peer.ip", netPeerIp);
    return this;
  }

  /**
   * Sets net.peer.port.
   *
   * @param netPeerPort Remote port number..
   */
  @Override
  public HttpClientSemanticConvention setNetPeerPort(long netPeerPort) {
    status.set(AttributeStatus.NET_PEER_PORT);
    delegate.setAttribute("net.peer.port", netPeerPort);
    return this;
  }

  /**
   * Sets net.peer.name.
   *
   * @param netPeerName Remote hostname or similar, see note below..
   */
  @Override
  public HttpClientSemanticConvention setNetPeerName(String netPeerName) {
    status.set(AttributeStatus.NET_PEER_NAME);
    delegate.setAttribute("net.peer.name", netPeerName);
    return this;
  }

  /**
   * Sets net.host.ip.
   *
   * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
   */
  @Override
  public HttpClientSemanticConvention setNetHostIp(String netHostIp) {
    status.set(AttributeStatus.NET_HOST_IP);
    delegate.setAttribute("net.host.ip", netHostIp);
    return this;
  }

  /**
   * Sets net.host.port.
   *
   * @param netHostPort Like `net.peer.port` but for the host port..
   */
  @Override
  public HttpClientSemanticConvention setNetHostPort(long netHostPort) {
    status.set(AttributeStatus.NET_HOST_PORT);
    delegate.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /**
   * Sets net.host.name.
   *
   * @param netHostName Local hostname or similar, see note below..
   */
  @Override
  public HttpClientSemanticConvention setNetHostName(String netHostName) {
    status.set(AttributeStatus.NET_HOST_NAME);
    delegate.setAttribute("net.host.name", netHostName);
    return this;
  }

  /** Builder class for {@link HttpClientSpan}. */
  public static class HttpClientSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected HttpClientSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public HttpClientSpanBuilder(Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public HttpClientSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public HttpClientSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public HttpClientSpanBuilder setKind(Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public HttpClientSpan start() {
      // check for sampling relevant field here, but there are none.
      return new HttpClientSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * Sets http.method.
     *
     * @param httpMethod HTTP request method..
     */
    public HttpClientSpanBuilder setHttpMethod(String httpMethod) {
      status.set(AttributeStatus.HTTP_METHOD);
      internalBuilder.setAttribute("http.method", httpMethod);
      return this;
    }

    /**
     * Sets http.url.
     *
     * @param httpUrl Full HTTP request URL in the form
     *     `scheme://host[:port]/path?query[#fragment]`. Usually the fragment is not transmitted
     *     over HTTP, but if it is known, it should be included nevertheless..
     */
    public HttpClientSpanBuilder setHttpUrl(String httpUrl) {
      status.set(AttributeStatus.HTTP_URL);
      internalBuilder.setAttribute("http.url", httpUrl);
      return this;
    }

    /**
     * Sets http.target.
     *
     * @param httpTarget The full request target as passed in a HTTP request line or equivalent..
     */
    public HttpClientSpanBuilder setHttpTarget(String httpTarget) {
      status.set(AttributeStatus.HTTP_TARGET);
      internalBuilder.setAttribute("http.target", httpTarget);
      return this;
    }

    /**
     * Sets http.host.
     *
     * @param httpHost The value of the [HTTP host
     *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
     *     present, this attribute should be the same..
     */
    public HttpClientSpanBuilder setHttpHost(String httpHost) {
      status.set(AttributeStatus.HTTP_HOST);
      internalBuilder.setAttribute("http.host", httpHost);
      return this;
    }

    /**
     * Sets http.scheme.
     *
     * @param httpScheme The URI scheme identifying the used protocol..
     */
    public HttpClientSpanBuilder setHttpScheme(String httpScheme) {
      status.set(AttributeStatus.HTTP_SCHEME);
      internalBuilder.setAttribute("http.scheme", httpScheme);
      return this;
    }

    /**
     * Sets http.status_code.
     *
     * @param httpStatusCode [HTTP response status
     *     code](https://tools.ietf.org/html/rfc7231#section-6)..
     */
    public HttpClientSpanBuilder setHttpStatusCode(long httpStatusCode) {
      status.set(AttributeStatus.HTTP_STATUS_CODE);
      internalBuilder.setAttribute("http.status_code", httpStatusCode);
      return this;
    }

    /**
     * Sets http.status_text.
     *
     * @param httpStatusText [HTTP reason
     *     phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2)..
     */
    public HttpClientSpanBuilder setHttpStatusText(String httpStatusText) {
      status.set(AttributeStatus.HTTP_STATUS_TEXT);
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
      status.set(AttributeStatus.HTTP_FLAVOR);
      internalBuilder.setAttribute("http.flavor", httpFlavor);
      return this;
    }

    /**
     * Sets http.user_agent.
     *
     * @param httpUserAgent Value of the [HTTP
     *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the
     *     client..
     */
    public HttpClientSpanBuilder setHttpUserAgent(String httpUserAgent) {
      status.set(AttributeStatus.HTTP_USER_AGENT);
      internalBuilder.setAttribute("http.user_agent", httpUserAgent);
      return this;
    }

    /**
     * Sets net.transport.
     *
     * @param netTransport Transport protocol used. See note below..
     */
    public HttpClientSpanBuilder setNetTransport(String netTransport) {
      status.set(AttributeStatus.NET_TRANSPORT);
      internalBuilder.setAttribute("net.transport", netTransport);
      return this;
    }

    /**
     * Sets net.peer.ip.
     *
     * @param netPeerIp Remote address of the peer (dotted decimal for IPv4 or
     *     [RFC5952](https://tools.ietf.org/html/rfc5952) for IPv6).
     */
    public HttpClientSpanBuilder setNetPeerIp(String netPeerIp) {
      status.set(AttributeStatus.NET_PEER_IP);
      internalBuilder.setAttribute("net.peer.ip", netPeerIp);
      return this;
    }

    /**
     * Sets net.peer.port.
     *
     * @param netPeerPort Remote port number..
     */
    public HttpClientSpanBuilder setNetPeerPort(long netPeerPort) {
      status.set(AttributeStatus.NET_PEER_PORT);
      internalBuilder.setAttribute("net.peer.port", netPeerPort);
      return this;
    }

    /**
     * Sets net.peer.name.
     *
     * @param netPeerName Remote hostname or similar, see note below..
     */
    public HttpClientSpanBuilder setNetPeerName(String netPeerName) {
      status.set(AttributeStatus.NET_PEER_NAME);
      internalBuilder.setAttribute("net.peer.name", netPeerName);
      return this;
    }

    /**
     * Sets net.host.ip.
     *
     * @param netHostIp Like `net.peer.ip` but for the host IP. Useful in case of a multi-IP host..
     */
    public HttpClientSpanBuilder setNetHostIp(String netHostIp) {
      status.set(AttributeStatus.NET_HOST_IP);
      internalBuilder.setAttribute("net.host.ip", netHostIp);
      return this;
    }

    /**
     * Sets net.host.port.
     *
     * @param netHostPort Like `net.peer.port` but for the host port..
     */
    public HttpClientSpanBuilder setNetHostPort(long netHostPort) {
      status.set(AttributeStatus.NET_HOST_PORT);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /**
     * Sets net.host.name.
     *
     * @param netHostName Local hostname or similar, see note below..
     */
    public HttpClientSpanBuilder setNetHostName(String netHostName) {
      status.set(AttributeStatus.NET_HOST_NAME);
      internalBuilder.setAttribute("net.host.name", netHostName);
      return this;
    }
  }
}
