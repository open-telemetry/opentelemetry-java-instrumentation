package io.opentelemetry.auto.typed.http.flat;

import io.opentelemetry.auto.typed.http.flat.HttpSpan.HttpSpanBuilder;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class HttpServerSpan {

  private static final Logger logger = Logger.getLogger(HttpServerSpan.class.getName());

  // Protected because maybe we want to extend manually these classes
  protected Span internalSpan;
  // to track required attributes
  Set<String> attributes;

  protected HttpServerSpan(Span span, Set<String> attributes) {
    this.internalSpan = span;
    this.attributes = new HashSet<>(attributes);
  }

  // No sampling relevant fields. So no extra parameter after the spanName.
  // But we have the requirement of a Kind.Server span
  public static HttpServerSpanBuilder createHttpServerSpan(Tracer tracer, String spanName) {
    return new HttpServerSpanBuilder(tracer, spanName).setKind(Span.Kind.SERVER);
    // if there would be sampling relevant attributes, we would also call the .set{attribute}
    // methods for these attributes
  }

  // we accept a builder from Http since Http Server "extends" Http
  public static HttpServerSpanBuilder createHttpServerSpan(HttpSpanBuilder builder) {
    return new HttpServerSpanBuilder(builder.getSpanBuilder(), builder.attributes);
  }

  /** @return the Span used internally */
  public Span getSpan() {
    return this.internalSpan;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  public void end() {
    internalSpan.end();
    // required attributes
    if (!attributes.contains("http.method")) {
      logger.warning("Wrong usage - Span missing http.method attribute");
    }
    // here we check for extra constraints. HttpServer has a single condition with four different
    // cases.
    boolean missing_anyof = true;
    if (attributes.contains("http.url")) {
      missing_anyof = false;
    }
    if (attributes.contains("http.scheme")
        && attributes.contains("http.host")
        && attributes.contains("http.target")) {
      missing_anyof = false;
    }
    if (attributes.contains("http.scheme")
        && attributes.contains("http.server_name")
        && attributes.contains("net.host.port")
        && attributes.contains("http.target")) {
      missing_anyof = false;
    }
    if (attributes.contains("http.scheme")
        && attributes.contains("net.host.name")
        && attributes.contains("net.host.port")
        && attributes.contains("http.target")) {
      missing_anyof = false;
    }
    if (missing_anyof) {
      logger.info("Constraint not respected!");
    }

    // here we check for conditional attributes and we report a warning if missing.
    if (!attributes.contains("http.status_code")) {
      logger.info("WARNING! Missing http.status_code attribute!");
    }
  }

  // these methods are used to check if the attribute is deleted
  private static void checkAttribute(String key, String value, Set<String> attributes) {
    if (value == null) {
      attributes.remove(key);
    } else {
      attributes.add(key);
    }
  }

  private static void checkAttribute(String key, long value, Set<String> attributes) {
    attributes.add(key);
  }

  private static void checkAttribute(String key, boolean value, Set<String> attributes) {
    attributes.add(key);
  }

  /** @param netHostPort Like `net.peer.port` but for the host port. */
  public HttpServerSpan setNetHostPort(long netHostPort) {
    checkAttribute("net.host.port", netHostPort, attributes);
    internalSpan.setAttribute("net.host.port", netHostPort);
    return this;
  }

  /** @param netHostName Local hostname or similar, see note below. */
  public HttpServerSpan setNetHostName(String netHostName) {
    checkAttribute("net.host.name", netHostName, attributes);
    internalSpan.setAttribute("net.host.name", netHostName);
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
  public HttpServerSpan setServerName(long server_name) {
    checkAttribute("http.server_name", server_name, attributes);
    internalSpan.setAttribute("http.server_name", server_name);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param route The matched route (path template).
   */
  public HttpServerSpan setRoute(String route) {
    internalSpan.setAttribute("http.route", route);
    return this;
  }

  /**
   * This is not necessarily the same as `net.peer.ip`, which would identify the network-level peer,
   * which may be a proxy.
   *
   * @param client_ip The IP address of the original client behind all proxies, if known (e.g. from
   *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
   */
  public HttpServerSpan setClientIp(String client_ip) {
    internalSpan.setAttribute("http.client_ip", client_ip);
    return this;
  }

  // All the remaining methods are duplicated from Http.
  // This will be automatically generated code so duplication is fine.

  /**
   * See http.server convention.
   *
   * @param method HTTP request method.
   */
  public HttpServerSpan setMethod(String method) {
    checkAttribute("http.method", method, attributes);
    internalSpan.setAttribute("http.method", method);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param url Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
   *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
   *     included nevertheless.
   */
  public HttpServerSpan setUrl(String url) {
    checkAttribute("http.url", url, attributes);
    internalSpan.setAttribute("http.url", url);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param target The full request target as passed in a HTTP request line or equivalent.
   * @since Semantic Convention 1.
   */
  public HttpServerSpan setTarget(String target) {
    checkAttribute("http.target", target, attributes);
    internalSpan.setAttribute("http.target", target);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param host The value of the [HTTP host
   *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
   *     present, this attribute should be the same.
   */
  public HttpServerSpan setHost(String host) {
    checkAttribute("http.host", host, attributes);
    internalSpan.setAttribute("http.host", host);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param scheme The URI scheme identifying the used protocol.
   */
  public HttpServerSpan setScheme(String scheme) {
    checkAttribute("http.scheme", scheme, attributes);
    internalSpan.setAttribute("http.scheme", scheme);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_code [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  public HttpServerSpan setStatusCode(long status_code) {
    // this might be tricky to handle in the template
    checkAttribute("http.status_code", status_code, attributes);
    internalSpan.setAttribute("http.status_code", status_code);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  public HttpServerSpan setStatusText(String status_text) {
    internalSpan.setAttribute("http.status_text", status_text);
    return this;
  }

  /**
   * If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor`
   * is `QUIC`, in which case `IP.UDP` is assumed.
   *
   * @param flavor Kind of HTTP protocol used.
   */
  public HttpServerSpan setFlavor(String flavor) {
    internalSpan.setAttribute("http.flavor", flavor);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param user_agent Value of the [HTTP
   *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
   */
  public HttpServerSpan setUserAgent(String user_agent) {
    internalSpan.setAttribute("http.user_agent", user_agent);
    return this;
  }

  public static class HttpServerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected Set<String> attributes;

    protected HttpServerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
      attributes = new HashSet<>();
    }

    public HttpServerSpanBuilder(Span.Builder spanBuilder, Set<String> attributes) {
      this.internalBuilder = spanBuilder;
      this.attributes = attributes;
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** this method is only available in the builder. * */
    public HttpServerSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts a span * */
    public HttpServerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new HttpServerSpan(
          this.internalBuilder.startSpan(), Collections.unmodifiableSet(attributes));
    }

    /** @param netHostPort Like `net.peer.port` but for the host port. */
    public HttpServerSpanBuilder setNetHostPort(long netHostPort) {
      checkAttribute("net.host.port", netHostPort, attributes);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return this;
    }

    /** @param netHostName Local hostname or similar, see note below. */
    public HttpServerSpanBuilder setNetHostName(String netHostName) {
      checkAttribute("net.host.name", netHostName, attributes);
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
      checkAttribute("http.server_name", server_name, attributes);
      internalBuilder.setAttribute("http.server_name", server_name);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param route The matched route (path template).
     */
    public HttpServerSpanBuilder setRoute(String route) {
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
      checkAttribute("http.method", method, attributes);
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
      checkAttribute("http.url", url, attributes);
      internalBuilder.setAttribute("http.url", url);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param target The full request target as passed in a HTTP request line or equivalent.
     */
    public HttpServerSpanBuilder setTarget(String target) {
      checkAttribute("http.target", target, attributes);
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
      checkAttribute("http.host", host, attributes);
      internalBuilder.setAttribute("http.host", host);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param scheme The URI scheme identifying the used protocol.
     */
    public HttpServerSpanBuilder setScheme(String scheme) {
      checkAttribute("http.scheme", scheme, attributes);
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
      checkAttribute("http.status_code", status_code, attributes);
      internalBuilder.setAttribute("http.status_code", status_code);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public HttpServerSpanBuilder setStatusText(String status_text) {
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
      internalBuilder.setAttribute("http.user_agent", user_agent);
      return this;
    }
  }
}
