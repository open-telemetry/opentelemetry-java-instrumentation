// This file includes software developed at SignalFx

package datadog.trace.instrumentation.springdata;

import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;

public final class SpringDataDecorator extends ClientDecorator {
  public static final SpringDataDecorator DECORATOR = new SpringDataDecorator();

  private SpringDataDecorator() {}

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"spring-data"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "spring-data";
  }

  public AgentSpan onOperation(final AgentSpan span, final Method method) {
    assert span != null;
    assert method != null;

    if (method != null) {
      span.setTag(DDTags.RESOURCE_NAME, spanNameForMethod(method));
    }
    return span;
  }
}
