package io.opentelemetry.auto.instrumentation.hystrix;

import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;

public class HystrixDecorator extends BaseDecorator {
  public static HystrixDecorator DECORATE = new HystrixDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[0];
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "hystrix";
  }

  public void onCommand(
      final Span span, final HystrixInvokableInfo<?> command, final String methodName) {
    if (command != null) {
      final String commandName = command.getCommandKey().name();
      final String groupName = command.getCommandGroup().name();
      final boolean circuitOpen = command.isCircuitBreakerOpen();

      final String resourceName = groupName + "." + commandName + "." + methodName;

      span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
      span.setAttribute("hystrix.command", commandName);
      span.setAttribute("hystrix.group", groupName);
      span.setAttribute("hystrix.circuit-open", circuitOpen);
    }
  }
}
