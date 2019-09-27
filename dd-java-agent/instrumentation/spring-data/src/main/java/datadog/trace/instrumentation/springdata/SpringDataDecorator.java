// This file includes software developed at SignalFx

package datadog.trace.instrumentation.springdata;

import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.interceptor.MutableSpan;
import io.opentracing.Span;
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

  public Span onOperation(final Span span, final Method method) {
    assert span != null;
    assert method != null;

    if (method != null) {
      if (span instanceof MutableSpan) {
        ((MutableSpan) span).setResourceName(spanNameForMethod(method));
      }
    }
    return span;
  }
}
