package datadog.trace.instrumentation.springdata;

import datadog.trace.agent.decorator.ClientDecorator;
import io.opentracing.Span;
import java.lang.reflect.Method;

public final class SpringDataDecorator extends ClientDecorator {
  public static final SpringDataDecorator DECORATOR = new SpringDataDecorator();

  private SpringDataDecorator() {}

  @Override
  protected String service() {
    return "spring-data";
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

  public Span onOperation(final Span span, final Method transactionalMethod) {
    assert span != null;
    assert transactionalMethod != null;

    if (transactionalMethod != null) {
      final Class<?> clazz = transactionalMethod.getDeclaringClass();
      final String methodName = transactionalMethod.getName();
      final String className = clazz.getSimpleName();
      final String operationName = className + "." + methodName;

      span.setOperationName(operationName);
    }
    return span;
  }
}
