package datadog.trace.instrumentation.netty40;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty40.client.HttpClientTracingHandler;
import datadog.trace.instrumentation.netty40.server.HttpServerTracingHandler;
import io.netty.util.AttributeKey;
import io.opentracing.Span;

public class AttributeKeys {
  public static final AttributeKey<TraceScope.Continuation>
      PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY =
          new AttributeKey<>("datadog.trace.instrumentation.netty40.parent.connect.continuation");

  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
      new AttributeKey<>(HttpServerTracingHandler.class.getName() + ".span");

  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      new AttributeKey<>(HttpClientTracingHandler.class.getName() + ".span");
}
