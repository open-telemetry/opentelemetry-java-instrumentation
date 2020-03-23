package datadog.trace.instrumentation.netty38.server;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import org.jboss.netty.handler.codec.http.HttpHeaders;

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
