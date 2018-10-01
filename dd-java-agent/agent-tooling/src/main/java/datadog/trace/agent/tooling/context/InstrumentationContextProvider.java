package datadog.trace.agent.tooling.context;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;

import java.util.List;

public interface InstrumentationContextProvider {

  /**
   * @return An AsmVisitorWrapper to run on the instrumentation's bytecode.
   */
  AsmVisitorWrapper getInstrumentationVisitor();

  /**
   * @return A list of classes in byte-array format. These classes will be injected into the runtime classloader.
   */
  List<byte[]> dynamicClasses();

  AgentBuilder additionalInstrumentation(AgentBuilder builder);

  // TODO: better place to put factory/creator
  class Creator {
    private Creator() {}

    public static InstrumentationContextProvider contextProviderFor(Instrumenter.Default instrumenter) {
      return new MapBackedProvider(instrumenter);
    }
  }
}
