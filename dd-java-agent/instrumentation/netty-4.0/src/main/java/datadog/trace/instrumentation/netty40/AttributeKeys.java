package datadog.trace.instrumentation.netty40;

import datadog.trace.bootstrap.WeakMap;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.netty40.client.HttpClientTracingHandler;
import datadog.trace.instrumentation.netty40.server.HttpServerTracingHandler;
import io.netty.util.AttributeKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttributeKeys {

  private static final WeakMap<ClassLoader, Map<String, AttributeKey<?>>> map =
      WeakMap.Implementation.DEFAULT.get();

  private static final WeakMap.ValueSupplier<Map<String, AttributeKey<?>>> mapSupplier =
      new WeakMap.ValueSupplier<Map<String, AttributeKey<?>>>() {
        @Override
        public Map<String, AttributeKey<?>> get() {
          return new ConcurrentHashMap<>();
        }
      };

  public static final AttributeKey<TraceScope.Continuation>
      PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY =
          attributeKey("datadog.trace.instrumentation.netty40.parent.connect.continuation");

  public static final AttributeKey<AgentSpan> SERVER_ATTRIBUTE_KEY =
      attributeKey(HttpServerTracingHandler.class.getName() + ".span");

  public static final AttributeKey<AgentSpan> CLIENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".span");

  public static final AttributeKey<AgentSpan> CLIENT_PARENT_ATTRIBUTE_KEY =
      attributeKey(HttpClientTracingHandler.class.getName() + ".parent");

  /**
   * Generate an attribute key or reuse the one existing in the global app map. This implementation
   * creates attributes only once even if the current class is loaded by several class loaders and
   * prevents an issue with Apache Atlas project were this class loaded by multiple class loaders,
   * while the Attribute class is loaded by a third class loader and used internally for the
   * cassandra driver.
   */
  private static <T> AttributeKey<T> attributeKey(String key) {
    Map<String, AttributeKey<?>> classLoaderMap =
        map.getOrCreate(AttributeKey.class.getClassLoader(), mapSupplier);
    if (classLoaderMap.containsKey(key)) {
      return (AttributeKey<T>) classLoaderMap.get(key);
    }

    final AttributeKey<T> value = new AttributeKey<>(key);
    classLoaderMap.put(key, value);
    return value;
  }
}
