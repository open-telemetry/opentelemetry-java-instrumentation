package datadog.trace.bootstrap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to track nested instrumentation.
 *
 * <p>For example, this can be used to track nested calls to super() in constructors by calling
 * #incrementCallDepth at the beginning of each constructor.
 */
public class CallDepthThreadLocalMap {
  private static final ThreadLocal<Map<Object, AtomicInteger>> TLS =
      new ThreadLocal<Map<Object, AtomicInteger>>() {
        @Override
        public Map<Object, AtomicInteger> initialValue() {
          return new HashMap<>();
        }
      };

  public static int incrementCallDepth(final Object k) {
    final Map<Object, AtomicInteger> map = TLS.get();
    AtomicInteger depth = map.get(k);
    if (depth == null) {
      depth = new AtomicInteger(0);
      map.put(k, depth);
      return 0;
    } else {
      return depth.incrementAndGet();
    }
  }

  public static void reset(final Object k) {
    final Map<Object, AtomicInteger> map = TLS.get();
    if (map != null) {
      map.remove(k);
    }
  }
}
