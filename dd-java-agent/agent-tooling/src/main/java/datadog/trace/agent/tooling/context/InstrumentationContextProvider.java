package datadog.trace.agent.tooling.context;

import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;

public interface InstrumentationContextProvider {

  /** @return An AsmVisitorWrapper to run on the instrumentation's bytecode. */
  AsmVisitorWrapper getInstrumentationVisitor();

  /**
   * @return A list of classes in byte-array format. These classes will be injected into the runtime
   *     classloader.
   */
  Map<String, byte[]> dynamicClasses();

  AgentBuilder additionalInstrumentation(AgentBuilder builder);

  // TODO: better place to put factory/creator
  class Creator {
    private Creator() {}

    public static InstrumentationContextProvider contextProviderFor(
        Instrumenter.Default instrumenter) {
      return new MapBackedProvider(instrumenter);
    }
  }
}
