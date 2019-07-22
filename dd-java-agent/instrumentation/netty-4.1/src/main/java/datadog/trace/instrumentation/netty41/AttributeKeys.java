package datadog.trace.instrumentation.netty41;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty41.client.HttpClientTracingHandler;
import datadog.trace.instrumentation.netty41.server.HttpServerTracingHandler;
import io.netty.util.AttributeKey;
import io.opentracing.Span;

public class AttributeKeys {
  public static final AttributeKey<TraceScope.Continuation>
      PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY =
          AttributeKey.valueOf("datadog.trace.instrumentation.netty41.parent.connect.continuation");

  /**
   * This constant is copied over to datadog.trace.instrumentation.ratpack.server.TracingHandler, so
   * if this changes, that must also change.
   */
  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
      AttributeKey.valueOf(HttpServerTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(HttpClientTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_PARENT_ATTRIBUTE_KEY =
      AttributeKey.valueOf(HttpClientTracingHandler.class.getName() + ".parent");
}
