package datadog.trace.agent.tooling.context;

/**
 * Instrumentation Context API
 */
public class InstrumentationContext {
  private InstrumentationContext() {}

  public static <T> T get(Object contextInstance, Class<T> contextClass) {
    throw new RuntimeException("calls to this method should be rewritten");
  }
}
