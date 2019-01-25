package datadog.trace.util.gc;

import java.lang.ref.WeakReference;

public abstract class GCUtils {

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
