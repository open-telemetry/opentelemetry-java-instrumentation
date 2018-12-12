package datadog.trace.tracer;

import java.lang.ref.WeakReference;

// TODO: stop copy-pasting this!
public class TestUtils {

  public static void awaitGC() {
    Object obj = new Object();
    final WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    awaitGC(ref);
  }

  public static void awaitGC(final WeakReference<?> ref) {
    while (ref.get() != null) {
      System.gc();
      System.runFinalization();
    }
  }
}
