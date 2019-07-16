package datadog.trace.instrumentation.netty40;

import datadog.trace.agent.tooling.ClassLoaderScopedWeakMap;
import datadog.trace.agent.tooling.ClassLoaderScopedWeakMapSupplier;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty40.client.HttpClientTracingHandler;
import datadog.trace.instrumentation.netty40.server.HttpServerTracingHandler;
import io.netty.util.AttributeKey;
import io.opentracing.Span;

public class AttributeKeys {

  static {
    System.out.println("Canone!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
  }

  private static final String PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY_NAME =
    "datadog.trace.instrumentation.netty40.parent.connect.continuation";

  public static final AttributeKey<TraceScope.Continuation>
    PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY = new AttributeKey<>(PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY_NAME);
//  public static final AttributeKey<TraceScope.Continuation>
//      PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY =
//          (AttributeKey<TraceScope.Continuation>)
//              ClassLoaderScopedWeakMap.INSTANCE.getOrCreate(
//                  AttributeKey.class.getClassLoader(),
//                  PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY_NAME,
//                  new ClassLoaderScopedWeakMap.Supplier() {
//                    @Override
//                    public Object get() {
//                      return new AttributeKey<>(PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY_NAME);
//                    }
//                  });

  private static final String SERVER_ATTRIBUTE_KEY_NAME =
    HttpServerTracingHandler.class.getName() + ".span";

  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY = new AttributeKey<>(SERVER_ATTRIBUTE_KEY_NAME);
//  public static final AttributeKey<Span> SERVER_ATTRIBUTE_KEY =
//      (AttributeKey<Span>)
//          ClassLoaderScopedWeakMap.INSTANCE.getOrCreate(
//              AttributeKey.class.getClassLoader(),
//              SERVER_ATTRIBUTE_KEY_NAME,
//              new ClassLoaderScopedWeakMap.Supplier() {
//                @Override
//                public Object get() {
//                  return new AttributeKey<>(SERVER_ATTRIBUTE_KEY_NAME);
//                }
//              });

  private static final String CLIENT_ATTRIBUTE_KEY_NAME =
    HttpClientTracingHandler.class.getName() + ".span";

  //  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY = new AttributeKey<>(CLIENT_ATTRIBUTE_KEY_NAME);
//  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY = (AttributeKey<Span>) ClassLoaderScopedWeakMap.aaa(new AttributeKey<>(CLIENT_ATTRIBUTE_KEY_NAME));
  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY = (AttributeKey<Span>) ClassLoaderScopedWeakMap.bbb(new ClassLoaderScopedWeakMapSupplier() {
    @Override
    public Object get() {
      System.out.println("[STD LOG] executing the getter");
      return new AttributeKey<>(CLIENT_ATTRIBUTE_KEY_NAME);
    }
  });
//  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY =
//      (AttributeKey<Span>)
//          ClassLoaderScopedWeakMap.INSTANCE.getOrCreate(
//              AttributeKey.class.getClassLoader(),
//              CLIENT_ATTRIBUTE_KEY_NAME,
//              new ClassLoaderScopedWeakMap.Supplier() {
//                @Override
//                public Object get() {
//                  return new AttributeKey<>(CLIENT_ATTRIBUTE_KEY_NAME);
//                }
//              });
}
