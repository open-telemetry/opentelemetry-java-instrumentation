package io.opentelemetry.auto.instrumentation.netty41.server;

import io.netty.handler.codec.http.HttpHeaders;
import io.opentelemetry.context.propagation.HttpTextFormat;

public class NettyRequestExtractAdapter implements HttpTextFormat.Getter<HttpHeaders> {

  public static final NettyRequestExtractAdapter GETTER = new NettyRequestExtractAdapter();

  @Override
  public String get(final HttpHeaders headers, final String key) {
    return headers.get(key);
  }
}
