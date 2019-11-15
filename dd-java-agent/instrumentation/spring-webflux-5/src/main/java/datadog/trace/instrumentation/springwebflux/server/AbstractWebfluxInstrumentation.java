package datadog.trace.instrumentation.springwebflux.server;

import datadog.trace.agent.tooling.Instrumenter;

public abstract class AbstractWebfluxInstrumentation extends Instrumenter.Default {

  public AbstractWebfluxInstrumentation(final String... additionalNames) {
    super("spring-webflux", additionalNames);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      packageName + ".SpringWebfluxHttpServerDecorator",
      // Some code comes from reactor's instrumentation's helper
      "datadog.trace.instrumentation.reactor.core.ReactorCoreAdviceUtils",
      "datadog.trace.instrumentation.reactor.core.ReactorCoreAdviceUtils$TracingSubscriber",
      packageName + ".AdviceUtils",
      packageName + ".RouteOnSuccessOrError"
    };
  }
}
