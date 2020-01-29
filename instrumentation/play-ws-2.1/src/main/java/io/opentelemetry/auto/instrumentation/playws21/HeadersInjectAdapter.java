package io.opentelemetry.auto.instrumentation.playws21;

import io.opentelemetry.context.propagation.HttpTextFormat;
import play.shaded.ahc.org.asynchttpclient.Request;

public class HeadersInjectAdapter implements HttpTextFormat.Setter<Request> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void put(final Request carrier, final String key, final String value) {
    carrier.getHeaders().add(key, value);
  }
}
