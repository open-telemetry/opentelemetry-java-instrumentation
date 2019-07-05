package datadog.trace.instrumentation.netty40;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty40.client.HttpClientTracingHandler;
import datadog.trace.instrumentation.netty40.server.HttpServerTracingHandler;
import io.netty.util.AttributeKey;
import io.opentracing.Span;

public class AttributeKeys {

  public static final AttributeKey<TraceScope.Continuation>
      PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY =
          new AttributeKey<>(
              buildContextSpecificKey(
                  "datadog.trace.instrumentation.netty40.parent.connect.continuation"));

  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
      new AttributeKey<>(
          buildContextSpecificKey(HttpServerTracingHandler.class.getName() + ".span"));

  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
      new AttributeKey<>(
          buildContextSpecificKey(HttpClientTracingHandler.class.getName() + ".span"));

  /**
   * Netty 4.0 before 4.0.26 handled differently how unique attributes where handled, with 4.0.26+
   * being more lenient with duplicates. We found a use case in Apache Atlas 1.1.0 where for some
   * reason, this class gets loaded by multiple class loaders generating an error in 4.0.25- before
   * an exception was thrown if that attribute was already defined.
   *
   * @param simpleKey The logical key assigned.
   * @return A key scoped to the current class loader in use, if not null.
   */
  private static String buildContextSpecificKey(final String simpleKey) {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    String key = simpleKey;
    if (contextClassLoader != null) {
      key =
          "ClassLoader."
              + contextClassLoader.getClass().getName()
              + "."
              + contextClassLoader.hashCode()
              + "."
              + key;
    }
    return key;
  }
}
