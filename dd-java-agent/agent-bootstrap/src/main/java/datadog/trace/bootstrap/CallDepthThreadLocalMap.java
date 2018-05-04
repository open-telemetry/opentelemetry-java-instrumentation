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
  private static final ThreadLocal<Map<Key, AtomicInteger>> TLS =
      new ThreadLocal<Map<Key, AtomicInteger>>() {
        @Override
        public Map<Key, AtomicInteger> initialValue() {
          return new HashMap<>();
        }
      };

  public static int incrementCallDepth(final Key k) {
    final Map<Key, AtomicInteger> map = TLS.get();
    AtomicInteger depth = map.get(k);
    if (depth == null) {
      depth = new AtomicInteger(0);
      map.put(k, depth);
      return 0;
    } else {
      return depth.incrementAndGet();
    }
  }

  public static void reset(final Key k) {
    final Map<Key, AtomicInteger> map = TLS.get();
    if (map != null) {
      map.remove(k);
    }
  }

  public enum Key {
    CLASSLOADER,
    CONNECTION,
    PREPARED_STATEMENT,
    STATEMENT
  }
}
