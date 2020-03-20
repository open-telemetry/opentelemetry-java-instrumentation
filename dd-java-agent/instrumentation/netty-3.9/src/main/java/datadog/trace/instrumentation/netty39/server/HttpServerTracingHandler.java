package datadog.trace.instrumentation.netty39.server;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.netty39.ChannelState;
import datadog.trace.instrumentation.netty39.util.CombinedSimpleChannelHandler;
import org.jboss.netty.channel.Channel;

public class HttpServerTracingHandler
    extends CombinedSimpleChannelHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler(final ContextStore<Channel, ChannelState> contextStore) {
    super(
        new HttpServerRequestTracingHandler(contextStore),
        new HttpServerResponseTracingHandler(contextStore));
  }
}
