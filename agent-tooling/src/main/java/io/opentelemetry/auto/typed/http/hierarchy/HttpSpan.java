package io.opentelemetry.auto.typed.http.hierarchy;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

public final class HttpSpan extends BasicHttpSpan<HttpSpan> {

  private static final Logger logger = Logger.getLogger(HttpSpan.class.getName());

  public HttpSpan(Span span, Set<String> attributes) {
    super(span, attributes);
  }

  @Override
  public void end() {
    internalSpan.end();
    checks();
  }

  void checks() {
    if (!attributes.contains("http.method")) {
      logger.warning("Wrong usage - Span missing http.method attribute");
    }
    // here we check for conditional attributes and we report a warning if missing.
    if (!attributes.contains("http.status_code")) {
      logger.info("WARNING! Missing http.status_code attribute!");
    }
  }

  public static class HttpSpanBuilder extends BasicHttpSpanBuilder<HttpSpan, HttpSpanBuilder> {

    protected HttpSpanBuilder(Tracer tracer, String spanName) {
      super(tracer, spanName);
    }

    @Override
    public HttpSpan start() {
      return new HttpSpan(
          super.internalBuilder.startSpan(), Collections.unmodifiableSet(attributes));
    }
  }
}
