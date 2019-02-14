package datadog.trace.instrumentation.springwebflux;

import datadog.trace.agent.tooling.Instrumenter;

public abstract class AbstractWebfluxInstrumentation extends Instrumenter.Default {

  public static final String PACKAGE = AbstractWebfluxInstrumentation.class.getPackage().getName();

  public AbstractWebfluxInstrumentation(final String... additionalNames) {
    super("spring-webflux", additionalNames);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      // Some code comes from reactor's instrumentation's helper
      "datadog.trace.instrumentation.reactor.core.ReactorCoreAdviceUtils",
      "datadog.trace.instrumentation.reactor.core.ReactorCoreAdviceUtils$TracingSubscriber",
      PACKAGE + ".AdviceUtils",
      PACKAGE + ".RouteOnSuccessOrError"
    };
  }
}
