package io.opentelemetry.auto.tooling.context;

import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;

public class NoopContextProvider implements InstrumentationContextProvider {

  public static NoopContextProvider INSTANCE = new NoopContextProvider();

  private NoopContextProvider() {}

  @Override
  public Extendable instrumentationTransformer(final Extendable builder) {
    return builder;
  }

  @Override
  public Extendable additionalInstrumentation(final Extendable builder) {
    return builder;
  }
}
