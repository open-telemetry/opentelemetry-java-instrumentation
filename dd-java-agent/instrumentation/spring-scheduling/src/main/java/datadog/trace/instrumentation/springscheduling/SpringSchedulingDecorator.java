// This file includes software developed at SignalFx

package datadog.trace.instrumentation.springscheduling;

import datadog.trace.agent.decorator.BaseDecorator;

public final class SpringSchedulingDecorator extends BaseDecorator {
  public static final SpringSchedulingDecorator DECORATOR = new SpringSchedulingDecorator();

  private SpringSchedulingDecorator() {}

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"spring-scheduling"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "spring-scheduling";
  }
}
