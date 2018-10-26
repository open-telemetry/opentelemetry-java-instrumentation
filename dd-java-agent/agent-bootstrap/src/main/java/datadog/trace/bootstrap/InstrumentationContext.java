package datadog.trace.bootstrap;

/** Instrumentation Context API */
public class InstrumentationContext {
  private InstrumentationContext() {}

  /**
   * Fetch a context instance out of the context store.
   *
   * <p>
   *
   * <p>Conceptually, this can be thought of as a two pass map look up.
   *
   * <p>For example: <em>RunnableState runnableState = get(runnableImpl, Runnable.class,
   * RunnableState.class)</em> --> <em>RunnableState runnableState = (RunnableState)
   * GlobalContextMap.get(Runnable.class).get(runnableImpl)</em>
   *
   * <p>
   *
   * <p>However, the implementation is actually provided by bytecode transformation for performance
   * reasons.
   *
   * <p>
   *
   * <p>Context classes are weakly referenced and will be garbage collected when their corresponding
   * user instance is collected.
   *
   * <p>
   *
   * <p>Instrumenters making this call must define the user-context class relationship in
   * datadog.trace.agent.tooling.Instrumenter.Default#contextStore.
   *
   * @param userInstance The instance to store context on.
   * @param userClass The user class context is attached to.
   * @param contextClass The context class attached to the user class.
   * @param <K> user class
   * @param <V> context class
   * @return The context instance attached to userInstance.
   */
  public static <K, V> V get(K userInstance, Class<K> userClass, Class<V> contextClass) {
    throw new RuntimeException("calls to this method will be rewritten");
  }
}
