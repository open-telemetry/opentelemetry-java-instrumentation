package io.opentelemetry.auto.typed.http.hierarchy;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Set;

public abstract class BasicHttpServerSpan<DerivedSpanT extends BasicHttpServerSpan<DerivedSpanT>>
    extends BasicHttpSpan<DerivedSpanT> {

  public BasicHttpServerSpan(Span span, Set<String> attributes) {
    super(span, attributes);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected DerivedSpanT self() {
    return (DerivedSpanT) this;
  }

  public abstract void end();

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
  public DerivedSpanT setNetHostPort(long netHostPort) {
    checkAttribute("net.host.port", netHostPort, attributes);
    internalSpan.setAttribute("net.host.port", netHostPort);
    return self();
  }

  /** @param netHostName Local hostname or similar, see note below. */
  public DerivedSpanT setNetHostName(String netHostName) {
    checkAttribute("net.host.name", netHostName, attributes);
    internalSpan.setAttribute("net.host.name", netHostName);
    return self();
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
  public DerivedSpanT setServerName(long server_name) {
    checkAttribute("http.server_name", server_name, attributes);
    internalSpan.setAttribute("http.server_name", server_name);
    return self();
  }

  /**
   * See http.server convention.
   *
   * @param route The matched route (path template).
   * @since Semantic Convention 1.
   */
  public DerivedSpanT setRoute(String route) {
    internalSpan.setAttribute("http.route", route);
    return self();
  }

  /**
   * This is not necessarily the same as `net.peer.ip`, which would identify the network-level peer,
   * which may be a proxy.
   *
   * @param client_ip The IP address of the original client behind all proxies, if known (e.g. from
   *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
   */
  public DerivedSpanT setClientIp(String client_ip) {
    internalSpan.setAttribute("http.client_ip", client_ip);
    return self();
  }

  public abstract static class BasicHttpServerSpanBuilder<
          DerivedSpanT extends BasicHttpServerSpan<DerivedSpanT>,
          DerivedBuilderT extends BasicHttpServerSpanBuilder<DerivedSpanT, DerivedBuilderT>>
      extends BasicHttpSpanBuilder<DerivedSpanT, DerivedBuilderT> {

    protected BasicHttpServerSpanBuilder(Tracer tracer, String spanName) {
      super(tracer, spanName);
    }

    public Span.Builder getSpanBuilder() {
      return this.internalBuilder;
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

    /** @param netHostPort Like `net.peer.port` but for the host port. */
    public DerivedBuilderT setNetHostPort(long netHostPort) {
      checkAttribute("net.host.port", netHostPort, attributes);
      internalBuilder.setAttribute("net.host.port", netHostPort);
      return self();
    }

    /** @param netHostName Local hostname or similar, see note below. */
    public DerivedBuilderT setNetHostName(String netHostName) {
      checkAttribute("net.host.name", netHostName, attributes);
      internalBuilder.setAttribute("net.host.name", netHostName);
      return self();
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
    public DerivedBuilderT setServerName(String server_name) {
      checkAttribute("http.server_name", server_name, attributes);
      internalBuilder.setAttribute("http.server_name", server_name);
      return self();
    }

    /**
     * See http.server convention.
     *
     * @param route The matched route (path template).
     */
    public DerivedBuilderT setRoute(String route) {
      internalBuilder.setAttribute("http.route", route);
      return self();
    }

    /**
     * This is not necessarily the same as `net.peer.ip`, which would identify the network-level
     * peer, which may be a proxy.
     *
     * @param client_ip The IP address of the original client behind all proxies, if known (e.g.
     *     from
     *     [X-Forwarded-For](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For)).
     */
    public DerivedBuilderT setClientIp(String client_ip) {
      internalBuilder.setAttribute("http.client_ip", client_ip);
      return self();
    }
  }
}
