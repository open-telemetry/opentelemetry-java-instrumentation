package datadog.trace.instrumentation.netty39.client;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.netty39.ChannelTraceContext;
import datadog.trace.instrumentation.netty39.util.CombinedSimpleChannelHandler;
import org.jboss.netty.channel.Channel;

public class HttpClientTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpClientResponseTracingHandler, HttpClientRequestTracingHandler> {

  public HttpClientTracingHandler(final ContextStore<Channel, ChannelTraceContext> contextStore) {
    super(
        new HttpClientResponseTracingHandler(contextStore),
        new HttpClientRequestTracingHandler(contextStore));
  }
}
