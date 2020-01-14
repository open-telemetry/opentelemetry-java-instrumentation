package io.opentelemetry.auto.instrumentation.springwebflux.server;

import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ServerDecorator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringWebfluxHttpServerDecorator extends ServerDecorator {
  public static final SpringWebfluxHttpServerDecorator DECORATE =
      new SpringWebfluxHttpServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"spring-webflux"};
  }

  @Override
  protected String spanType() {
    return SpanTypes.HTTP_SERVER;
  }

  @Override
  protected String component() {
    return "spring-webflux-controller";
  }
}
