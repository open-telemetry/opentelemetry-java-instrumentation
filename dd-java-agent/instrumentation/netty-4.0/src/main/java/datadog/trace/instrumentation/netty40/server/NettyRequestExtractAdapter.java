package datadog.trace.instrumentation.netty40.server;

import datadog.trace.instrumentation.api.AgentPropagation;
import io.netty.handler.codec.http.HttpRequest;

public class NettyRequestExtractAdapter implements AgentPropagation.Getter<HttpRequest> {

  public static final NettyRequestExtractAdapter GETTER = new NettyRequestExtractAdapter();

  @Override
  public Iterable<String> keys(final HttpRequest carrier) {
    return carrier.headers().names();
  }

  @Override
  public String get(final HttpRequest carrier, final String key) {
    return carrier.headers().get(key);
  }
}
