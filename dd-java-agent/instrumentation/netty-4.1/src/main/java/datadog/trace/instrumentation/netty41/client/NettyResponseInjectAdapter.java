package datadog.trace.instrumentation.netty41.client;

import datadog.trace.instrumentation.api.AgentPropagation;
import io.netty.handler.codec.http.HttpRequest;

public class NettyResponseInjectAdapter implements AgentPropagation.Setter<HttpRequest> {

  public static final NettyResponseInjectAdapter SETTER = new NettyResponseInjectAdapter();

  @Override
  public void set(final HttpRequest carrier, final String key, final String value) {
    carrier.headers().set(key, value);
  }
}
