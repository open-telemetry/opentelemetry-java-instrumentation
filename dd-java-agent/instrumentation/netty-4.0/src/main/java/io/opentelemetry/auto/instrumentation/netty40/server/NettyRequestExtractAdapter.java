package io.opentelemetry.auto.instrumentation.netty40.server;

import io.netty.handler.codec.http.HttpHeaders;
import io.opentelemetry.auto.instrumentation.api.AgentPropagation;

public class NettyRequestExtractAdapter implements AgentPropagation.Getter<HttpHeaders> {

  public static final NettyRequestExtractAdapter GETTER = new NettyRequestExtractAdapter();

  @Override
  public Iterable<String> keys(final HttpHeaders headers) {
    return headers.names();
  }

  @Override
  public String get(final HttpHeaders headers, final String key) {
    return headers.get(key);
  }
}
