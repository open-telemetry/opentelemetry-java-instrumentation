package dd.trace;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;

/** A bytebuddy advice builder with default DataDog settings. */
@Slf4j
public class DDAdvice extends AgentBuilder.Transformer.ForAdvice {
  private static ClassLoader AGENT_CLASSLOADER;

  static {
    try {
      Class<?> agentClass =
          DDAdvice.class.getClassLoader().loadClass("com.datadoghq.agent.TracingAgent");
      Method getAgentClassloaderMethod = agentClass.getMethod("getAgentClassLoader");
      AGENT_CLASSLOADER = (ClassLoader) getAgentClassloaderMethod.invoke(null);
    } catch (Throwable t) {
      log.error("Unable to locate agent classloader. Falling back to System Classloader");
      AGENT_CLASSLOADER = ClassLoader.getSystemClassLoader();
    }
  }

  public static AgentBuilder.Transformer.ForAdvice create() {
    return new DDAdvice()
        .with(
            new AgentBuilder.LocationStrategy.Simple(
                ClassFileLocator.ForClassLoader.of(AGENT_CLASSLOADER)))
        .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler());
  }

  private DDAdvice() {}
}
