package io.opentelemetry.auto.typed.http.hierarchy;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.HashSet;
import java.util.Set;

public abstract class BasicHttpSpan<DerivedSpanT extends BasicHttpSpan<DerivedSpanT>> {

  protected Span internalSpan;
  Set<String> attributes;

  public BasicHttpSpan(Span span, Set<String> attributes) {
    this.internalSpan = span;
    this.attributes = new HashSet<>(attributes);
  }

  @SuppressWarnings("unchecked")
  protected DerivedSpanT self() {
    return (DerivedSpanT) this;
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

  public abstract void end();

  /**
   * See http.server convention.
   *
   * @param method HTTP request method.
   */
  public DerivedSpanT setMethod(String method) {
    checkAttribute("http.method", method, attributes);
    internalSpan.setAttribute("http.method", method);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param url Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
   *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
   *     included nevertheless.
   */
  public DerivedSpanT setUrl(String url) {
    checkAttribute("http.url", url, attributes);
    internalSpan.setAttribute("http.url", url);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param target The full request target as passed in a HTTP request line or equivalent.
   */
  public DerivedSpanT setTarget(String target) {
    checkAttribute("http.target", target, attributes);
    internalSpan.setAttribute("http.target", target);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param host The value of the [HTTP host
   *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
   *     present, this attribute should be the same.
   */
  public DerivedSpanT setHost(String host) {
    checkAttribute("http.host", host, attributes);
    internalSpan.setAttribute("http.host", host);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param scheme The URI scheme identifying the used protocol.
   */
  public DerivedSpanT setScheme(String scheme) {
    checkAttribute("http.scheme", scheme, attributes);
    internalSpan.setAttribute("http.scheme", scheme);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param status_code [HTTP response status code](https://tools.ietf.org/html/rfc7231#section-6).
   */
  public DerivedSpanT setStatusCode(long status_code) {
    checkAttribute("http.status_code", status_code, attributes);
    internalSpan.setAttribute("http.status_code", status_code);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
   */
  public DerivedSpanT setStatusText(String status_text) {
    internalSpan.setAttribute("http.status_text", status_text);
    return self();
  }

  /**
   * If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor`
   * is `QUIC`, in which case `IP.UDP` is assumed.
   *
   * @param flavor Kind of HTTP protocol used.
   */
  public DerivedSpanT setFlavor(String flavor) {
    internalSpan.setAttribute("http.flavor", flavor);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param user_agent Value of the [HTTP
   *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
   */
  public DerivedSpanT setUserAgent(String user_agent) {
    internalSpan.setAttribute("http.user_agent", user_agent);
    return self();
  }

  public abstract static class BasicHttpSpanBuilder<
      DerivedSpanT extends BasicHttpSpan<DerivedSpanT>,
      DerivedBuilderT extends BasicHttpSpanBuilder<DerivedSpanT, DerivedBuilderT>> {

    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;
    protected Set<String> attributes;

    protected BasicHttpSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
      attributes = new HashSet<>();
    }

    @SuppressWarnings("unchecked")
    protected DerivedBuilderT self() {
      return (DerivedBuilderT) this;
    }

    public DerivedBuilderT setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return self();
    }

    public abstract DerivedSpanT start();

    /**
     * See http.server convention.
     *
     * @param method HTTP request method.
     */
    public DerivedBuilderT setMethod(String method) {
      checkAttribute("http.method", method, attributes);
      internalBuilder.setAttribute("http.method", method);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param url Full HTTP request URL in the form `scheme://host[:port]/path?query[#fragment]`.
     *     Usually the fragment is not transmitted over HTTP, but if it is known, it should be
     *     included nevertheless.
     */
    public DerivedBuilderT setUrl(String url) {
      checkAttribute("http.url", url, attributes);
      internalBuilder.setAttribute("http.url", url);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param target The full request target as passed in a HTTP request line or equivalent.
     */
    public DerivedBuilderT setTarget(String target) {
      checkAttribute("http.target", target, attributes);
      internalBuilder.setAttribute("http.target", target);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param host The value of the [HTTP host
     *     header](https://tools.ietf.org/html/rfc7230#section-5.4). When the header is empty or not
     *     present, this attribute should be the same.
     */
    public DerivedBuilderT setHost(String host) {
      checkAttribute("http.host", host, attributes);
      internalBuilder.setAttribute("http.host", host);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param scheme The URI scheme identifying the used protocol.
     */
    public DerivedBuilderT setScheme(String scheme) {
      checkAttribute("http.scheme", scheme, attributes);
      internalBuilder.setAttribute("http.scheme", scheme);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param status_code [HTTP response status
     *     code](https://tools.ietf.org/html/rfc7231#section-6).
     */
    public DerivedBuilderT setStatusCode(long status_code) {
      checkAttribute("http.status_code", status_code, attributes);
      internalBuilder.setAttribute("http.status_code", status_code);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param status_text [HTTP reason phrase](https://tools.ietf.org/html/rfc7230#section-3.1.2).
     */
    public DerivedBuilderT setStatusText(String status_text) {
      internalBuilder.setAttribute("http.status_text", status_text);
      return self();
    }

    /**
     * If `net.transport` is not specified, it can be assumed to be `IP.TCP` except if `http.flavor`
     * is `QUIC`, in which case `IP.UDP` is assumed.
     *
     * @param flavor Kind of HTTP protocol used.
     */
    public DerivedBuilderT setFlavor(String flavor) {
      internalBuilder.setAttribute("http.flavor", flavor);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param user_agent Value of the [HTTP
     *     User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3) header sent by the client.
     */
    public DerivedBuilderT setUserAgent(String user_agent) {
      internalBuilder.setAttribute("http.user_agent", user_agent);
      return self();
    }
  }
}
