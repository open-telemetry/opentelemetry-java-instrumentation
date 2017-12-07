package dd.trace;

import net.bytebuddy.agent.builder.AgentBuilder;

/** A bytebuddy advice builder with default DataDog settings. */
public class DDAdvice extends AgentBuilder.Transformer.ForAdvice {
  public static AgentBuilder.Transformer.ForAdvice create() {
    return new DDAdvice()
        .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler());
  }

  private DDAdvice() {}
}
