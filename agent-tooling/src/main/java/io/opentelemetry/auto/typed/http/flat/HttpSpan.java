package io.opentelemetry.auto.typed.http.flat;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class HttpSpan {

  private static final Logger logger = Logger.getLogger(HttpSpan.class.getName());

  // Protected because maybe we want to extend manually these classes
  protected Span internalSpan;
  // required attributes
  Set<String> attributes;

  protected HttpSpan(Span span, Set<String> attributes) {
    this.internalSpan = span;
    this.attributes = new HashSet<>(attributes);
  }

  // No sampling relevant fields. So no extra parameter after the spanName.
  public static HttpSpanBuilder createHttpSpan(Tracer tracer, String spanName) {
    return new HttpSpanBuilder(tracer, spanName);
    // if there would be a sampling relevant attribute, we would also call the .set{attribute}
    // methods for these attributes
  }

  public Span getSpan() {
    return this.internalSpan;
  }

  public void end() {
    internalSpan.end();
    // required attributes
    if (!attributes.contains("http.method")) {
      logger.warning("Wrong usage - Span missing http.method attribute");
    }
    // here we check for constraints. We don't have any in Http Span

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

  /**
   * See http.server convention.
   *
   * @param method HTTP request method.
   */
  public HttpSpan setMethod(String method) {
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
  public HttpSpan setUrl(String url) {
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
  public HttpSpan setTarget(String target) {
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
  public HttpSpan setHost(String host) {
    checkAttribute("http.host", host, attributes);
    internalSpan.setAttribute("http.host", host);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param scheme The URI scheme identifying the used protocol.
   */
  public HttpSpan setScheme(String scheme) {
    checkAttribute("http.scheme", scheme, attributes);
    internalSpan.setAttribute("http.scheme", scheme);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param status_code [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  public HttpSpan setStatusCode(long status_code) {
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
  public HttpSpan setStatusText(String status_text) {
    internalSpan.setAttribute("http.status_text", status_text);
    return this;
  }

  /**
   * If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor`
   * is `QUIC`, in which case `IP.UDP` is assumed.
   *
   * @param flavor Kind of HTTP protocol used.
   */
  public HttpSpan setFlavor(String flavor) {
    internalSpan.setAttribute("http.flavor", flavor);
    return this;
  }

  /**
   * See http.server convention.
   *
   * @param user_agent Value of the [HTTP
   *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
   */
  public HttpSpan setUserAgent(String user_agent) {
    internalSpan.setAttribute("http.user_agent", user_agent);
    return this;
  }

  public static class HttpSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected Set<String> attributes;

    protected HttpSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
      attributes = new HashSet<>();
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** this method is only available in the builder. * */
    public HttpSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts a span * */
    public HttpSpan start() {
      // check for sampling relevant field here, but there are none.
      return new HttpSpan(
          this.internalBuilder.startSpan(), Collections.unmodifiableSet(attributes));
    }

    /**
     * See http.server convention.
     *
     * @param method HTTP request method.
     */
    public HttpSpanBuilder setMethod(String method) {
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
    public HttpSpanBuilder setUrl(String url) {
      checkAttribute("http.url", url, attributes);
      internalBuilder.setAttribute("http.url", url);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param target The full request target as passed in a HTTP request line or equivalent.
     */
    public HttpSpanBuilder setTarget(String target) {
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
    public HttpSpanBuilder setHost(String host) {
      checkAttribute("http.host", host, attributes);
      internalBuilder.setAttribute("http.host", host);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param scheme The URI scheme identifying the used protocol.
     */
    public HttpSpanBuilder setScheme(String scheme) {
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
    public HttpSpanBuilder setStatusCode(long status_code) {
      checkAttribute("http.status_code", status_code, attributes);
      internalBuilder.setAttribute("http.status_code", status_code);
      return this;
    }

    /**
     * See http.server convention.
     *
     * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public HttpSpanBuilder setStatusText(String status_text) {
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
      internalBuilder.setAttribute("http.user_agent", user_agent);
      return this;
    }
  }
}
