package io.opentelemetry.auto.typed.http.hierarchy;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

public final class HttpServerSpan extends BasicHttpServerSpan<HttpServerSpan> {

  private static final Logger logger = Logger.getLogger(HttpServerSpan.class.getName());

  public HttpServerSpan(Span startSpan, Set<String> unmodifiableSet) {
    super(startSpan, unmodifiableSet);
  }

  public static class HttpServerSpanBuilder
      extends BasicHttpServerSpanBuilder<HttpServerSpan, HttpServerSpanBuilder> {

    protected HttpServerSpanBuilder(Tracer tracer, String spanName) {
      super(tracer, spanName);
    }

    @Override
    public HttpServerSpan start() {
      // check for sampling relevant here, but we have none
      return new HttpServerSpan(
          super.internalBuilder.startSpan(), Collections.unmodifiableSet(attributes));
    }
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
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

  // No sampling relevant fields. So no extra parameter after the spanName.
  // But we have the requirement of a Kind.Server span
  public static HttpServerSpanBuilder createHttpServerSpan(Tracer tracer, String spanName) {
    return new HttpServerSpanBuilder(tracer, spanName).setKind(Span.Kind.SERVER);
    // if there would be sampling relevant attributes, we would also call the .set{attribute}
    // methods for these attributes
  }
}
