package datadog.trace.bootstrap;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to track nested instrumentation.
 *
 * <p>For example, this can be used to track nested calls to super() in constructors by calling
 * #incrementCallDepth at the beginning of each constructor.
 */
public class CallDepthThreadLocalMap {
  private static final ThreadLocal<Map<Object, Integer>> TLS =
      new ThreadLocal<Map<Object, Integer>>() {
        @Override
        public Map<Object, Integer> initialValue() {
          return new HashMap<>();
        }
      };

  public static int incrementCallDepth(final Object k) {
    final Map<Object, Integer> map = TLS.get();
    Integer depth = map.get(k);
    if (depth == null) {
      depth = 0;
    } else {
      depth += 1;
    }
    map.put(k, depth);
    return depth;
  }

  public static void reset(final Object k) {
    TLS.get().remove(k);
  }
}
