package datadog.trace.instrumentation.classloaders;

import java.util.concurrent.atomic.AtomicInteger;

public class CallDepthThreadLocalMap {
  private static final ThreadLocal<AtomicInteger> tls = new ThreadLocal<>();

  public static int incrementCallDepth() {
    AtomicInteger depth = tls.get();
    if (depth == null) {
      depth = new AtomicInteger(0);
      tls.set(depth);
      return 0;
    } else {
      return depth.incrementAndGet();
    }
  }

  public static void reset() {
    tls.remove();
  }
}
