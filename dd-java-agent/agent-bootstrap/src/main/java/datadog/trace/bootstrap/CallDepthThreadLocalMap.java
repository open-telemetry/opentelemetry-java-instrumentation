package datadog.trace.bootstrap;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to track nested instrumentation.
 *
 * <p>For example, this can be used to track nested calls to super() in constructors by calling
 * #incrementCallDepth at the beginning of each constructor.
 */
public class CallDepthThreadLocalMap {
  private static final ThreadLocal<Map<Object, CallDepthThreadLocalMap>> INSTANCES =
      new ThreadLocal<>();

  private static final ThreadLocal<AtomicInteger> tls = new ThreadLocal<>();

  public static CallDepthThreadLocalMap get(final Object o) {
    if (INSTANCES.get() == null) {
      INSTANCES.set(new WeakHashMap<Object, CallDepthThreadLocalMap>());
    }
    if (!INSTANCES.get().containsKey(o)) {
      INSTANCES.get().put(o, new CallDepthThreadLocalMap());
    }
    return INSTANCES.get().get(o);
  }

  private CallDepthThreadLocalMap() {}

  public int incrementCallDepth() {
    AtomicInteger depth = tls.get();
    if (depth == null) {
      depth = new AtomicInteger(0);
      tls.set(depth);
      return 0;
    } else {
      return depth.incrementAndGet();
    }
  }

  public void reset() {
    tls.remove();
    INSTANCES.get().remove(this);
  }
}
