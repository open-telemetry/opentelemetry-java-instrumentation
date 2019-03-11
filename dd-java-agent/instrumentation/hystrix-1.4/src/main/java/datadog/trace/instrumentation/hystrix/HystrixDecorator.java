package datadog.trace.instrumentation.hystrix;

import com.netflix.hystrix.HystrixCommand;
import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;

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
      final Scope scope, final HystrixCommand<?> command, final String methodName) {
    if (command != null) {
      final String commandName = command.getCommandKey().name();
      final String groupName = command.getCommandGroup().name();
      final boolean circuitOpen = command.isCircuitBreakerOpen();

      final String resourceName = groupName + "." + commandName + "." + methodName;

      final Span span = scope.span();
      span.setTag(DDTags.RESOURCE_NAME, resourceName);
      span.setTag("hystrix.command", commandName);
      span.setTag("hystrix.group", groupName);
      span.setTag("hystrix.circuit-open", circuitOpen);
    }
  }
}
