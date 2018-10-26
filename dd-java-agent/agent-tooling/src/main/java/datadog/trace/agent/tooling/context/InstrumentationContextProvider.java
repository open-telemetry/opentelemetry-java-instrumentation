package datadog.trace.agent.tooling.context;

import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;

public interface InstrumentationContextProvider {

  /** @return An AsmVisitorWrapper to run on the instrumentation's bytecode. */
  AsmVisitorWrapper getInstrumentationVisitor();

  /**
   * @return A map of dynamic-class-name -> dynamic-class-bytes. These classes will be injected into
   *     the runtime classloader.
   */
  Map<String, byte[]> dynamicClasses();

  /** Hook for the context impl to define additional instrumentation if needed. */
  AgentBuilder additionalInstrumentation(AgentBuilder builder);
}
