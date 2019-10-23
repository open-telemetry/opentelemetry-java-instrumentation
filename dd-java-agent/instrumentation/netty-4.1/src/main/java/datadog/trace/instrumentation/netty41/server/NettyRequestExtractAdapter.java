package datadog.trace.instrumentation.netty41.server;

import datadog.trace.instrumentation.api.AgentPropagation;
import io.netty.handler.codec.http.HttpHeaders;

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
