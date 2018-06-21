package datadog.trace.instrumentation.netty41.client;

import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.util.AttributeKey;
import io.opentracing.Span;

public class HttpClientTracingHandler
    extends CombinedChannelDuplexHandler<
        HttpClientResponseTracingHandler, HttpClientRequestTracingHandler> {

  static final AttributeKey<Span> attributeKey =
      AttributeKey.valueOf(HttpClientTracingHandler.class.getName());

  public HttpClientTracingHandler() {
    super(new HttpClientResponseTracingHandler(), new HttpClientRequestTracingHandler());
  }
}
