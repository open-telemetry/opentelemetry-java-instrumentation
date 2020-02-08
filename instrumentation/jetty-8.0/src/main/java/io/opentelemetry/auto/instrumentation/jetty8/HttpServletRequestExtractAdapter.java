package io.opentelemetry.auto.instrumentation.jetty8;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestExtractAdapter implements HttpTextFormat.Getter<HttpServletRequest> {

  public static final HttpServletRequestExtractAdapter GETTER =
      new HttpServletRequestExtractAdapter();

  @Override
  public String get(final HttpServletRequest carrier, final String key) {
    return carrier.getHeader(key);
  }
}
