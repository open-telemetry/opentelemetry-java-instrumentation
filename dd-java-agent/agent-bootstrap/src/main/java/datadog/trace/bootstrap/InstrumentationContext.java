package datadog.trace.bootstrap;

/** Instrumentation Context API */
public class InstrumentationContext {
  private InstrumentationContext() {}

  public static <K, V> V get(Object userInstance, Class<K> userClass, Class<V> contextClass) {
    throw new RuntimeException("calls to this method should be rewritten");
  }
}
