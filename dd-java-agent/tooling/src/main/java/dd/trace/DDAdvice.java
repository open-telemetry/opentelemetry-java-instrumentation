package dd.trace;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;

/** A bytebuddy advice builder with default DataDog settings. */
@Slf4j
public class DDAdvice extends AgentBuilder.Transformer.ForAdvice {
  private static ClassLoader AGENT_CLASSLOADER;
  private static ClassFileLocator AGENT_CLASS_LOCATOR;

  static {
    try {
      Class<?> agentClass =
          DDAdvice.class.getClassLoader().loadClass("com.datadoghq.agent.TracingAgent");
      Method getAgentClassloaderMethod = agentClass.getMethod("getAgentClassLoader");
      AGENT_CLASSLOADER = (ClassLoader) getAgentClassloaderMethod.invoke(null);
    } catch (Throwable t) {
      AGENT_CLASSLOADER = DDAdvice.class.getClassLoader();
      log.error(
          "Failed to locate agent classloader. Falling back to "
              + AGENT_CLASSLOADER
              + " -- "
              + t.getMessage());
    }
    AGENT_CLASS_LOCATOR = ClassFileLocator.ForClassLoader.of(AGENT_CLASSLOADER);
  }

  /** Return the classloader the datadog agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return AGENT_CLASSLOADER;
  }

  /**
   * Create bytebuddy advice with default datadog settings.
   *
   * @return the bytebuddy advice
   */
  public static AgentBuilder.Transformer.ForAdvice create() {
    return new DDAdvice()
        .with(new AgentBuilder.LocationStrategy.Simple(AGENT_CLASS_LOCATOR))
        .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler());
  }

  private DDAdvice() {}
}
