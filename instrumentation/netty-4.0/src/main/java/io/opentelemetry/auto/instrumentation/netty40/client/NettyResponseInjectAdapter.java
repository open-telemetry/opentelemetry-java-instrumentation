package io.opentelemetry.auto.instrumentation.netty40.client;

import io.netty.handler.codec.http.HttpHeaders;
import io.opentelemetry.auto.instrumentation.api.AgentPropagation;

public class NettyResponseInjectAdapter implements AgentPropagation.Setter<HttpHeaders> {

  public static final NettyResponseInjectAdapter SETTER = new NettyResponseInjectAdapter();

  @Override
  public void set(final HttpHeaders headers, final String key, final String value) {
    headers.set(key, value);
  }
}
