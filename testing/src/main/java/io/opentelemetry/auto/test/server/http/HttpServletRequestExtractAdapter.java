package io.opentelemetry.auto.test.server.http;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.servlet.http.HttpServletRequest;

/**
 * Tracer extract adapter for {@link HttpServletRequest}.
 *
 * @author Pavol Loffay
 */
// FIXME:  This code is duplicated in several places.  Extract to a common dependency.
public class HttpServletRequestExtractAdapter implements HttpTextFormat.Getter<HttpServletRequest> {

  public static final HttpServletRequestExtractAdapter GETTER =
      new HttpServletRequestExtractAdapter();

  @Override
  public String get(final HttpServletRequest carrier, final String key) {
    return carrier.getHeader(key);
  }
}
