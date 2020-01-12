package io.opentelemetry.auto.instrumentation.netty40.server;

import io.netty.channel.CombinedChannelDuplexHandler;

public class HttpServerTracingHandler
    extends CombinedChannelDuplexHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  public HttpServerTracingHandler() {
    super(new HttpServerRequestTracingHandler(), new HttpServerResponseTracingHandler());
  }
}
