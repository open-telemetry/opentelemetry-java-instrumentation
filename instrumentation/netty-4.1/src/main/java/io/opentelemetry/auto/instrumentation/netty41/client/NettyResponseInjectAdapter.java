package io.opentelemetry.auto.instrumentation.netty41.client;

import io.netty.handler.codec.http.HttpHeaders;
import io.opentelemetry.context.propagation.HttpTextFormat;

public class NettyResponseInjectAdapter implements HttpTextFormat.Setter<HttpHeaders> {

  public static final NettyResponseInjectAdapter SETTER = new NettyResponseInjectAdapter();

  @Override
  public void put(final HttpHeaders headers, final String key, final String value) {
    headers.set(key, value);
  }
}
