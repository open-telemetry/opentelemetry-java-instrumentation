package dd.trace;

import com.datadoghq.agent.Utils;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.dynamic.ClassFileLocator;

/** A bytebuddy advice builder with default DataDog settings. */
public class DDAdvice extends AgentBuilder.Transformer.ForAdvice {
  private static final ClassFileLocator AGENT_CLASS_LOCATOR =
      ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader());
  /** Location strategy for resolving classes in the agent's jar. */
  private static final LocationStrategy AGENT_CLASS_LOCATION_STRATEGY =
      new AgentBuilder.LocationStrategy.Simple(AGENT_CLASS_LOCATOR);

  /**
   * Create bytebuddy advice with default datadog settings.
   *
   * @return the bytebuddy advice
   */
  public static AgentBuilder.Transformer.ForAdvice create() {
    return new DDAdvice()
        .with(AGENT_CLASS_LOCATION_STRATEGY)
        .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler());
  }

  private DDAdvice() {}
}
