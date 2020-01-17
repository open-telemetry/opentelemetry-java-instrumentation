package io.opentelemetry.auto.instrumentation.servlet3;

import io.opentelemetry.context.propagation.HttpTextFormat;

import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestExtractAdapter implements HttpTextFormat.Getter<HttpServletRequest> {

  public static final HttpServletRequestExtractAdapter GETTER =
      new HttpServletRequestExtractAdapter();

  @Override
  public String get(final HttpServletRequest carrier, final String key) {
    /*
     * Read from the attributes and override the headers.
     * This is used by HttpServletRequestSetter when a request is async-dispatched.
     */
    final Object attribute = carrier.getAttribute(key);
    if (attribute instanceof String) {
      return (String) attribute;
    }
    return carrier.getHeader(key);
  }
}
