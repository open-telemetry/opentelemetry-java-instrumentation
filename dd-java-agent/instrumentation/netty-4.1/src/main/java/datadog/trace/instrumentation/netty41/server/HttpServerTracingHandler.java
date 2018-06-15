package datadog.trace.instrumentation.netty41.server;

import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.util.AttributeKey;
import io.opentracing.Span;

public class HttpServerTracingHandler
    extends CombinedChannelDuplexHandler<
        HttpServerRequestTracingHandler, HttpServerResponseTracingHandler> {

  static final AttributeKey<Span> attributeKey =
      AttributeKey.valueOf(HttpServerTracingHandler.class.getName());

  public HttpServerTracingHandler() {
    super(new HttpServerRequestTracingHandler(), new HttpServerResponseTracingHandler());
  }
}
