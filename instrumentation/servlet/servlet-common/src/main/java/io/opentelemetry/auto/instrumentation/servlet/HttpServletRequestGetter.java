package io.opentelemetry.auto.instrumentation.servlet;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestGetter implements HttpTextFormat.Getter<HttpServletRequest> {

  public static final HttpServletRequestGetter GETTER = new HttpServletRequestGetter();

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }
}
