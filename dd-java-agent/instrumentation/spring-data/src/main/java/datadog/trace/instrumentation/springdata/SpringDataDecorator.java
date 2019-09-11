package datadog.trace.instrumentation.springdata;

import datadog.trace.agent.decorator.OrmClientDecorator;
import io.opentracing.Span;
import java.lang.reflect.Method;

public class SpringDataDecorator extends OrmClientDecorator {
  public static final SpringDataDecorator DECORATOR = new SpringDataDecorator();

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

  @Override
  protected String dbType() {
    return null;
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbInstance(final Object o) {
    return null;
  }

  @Override
  public String entityName(final Object entity) {
    String name = null;

    if (entity instanceof Method) {
      final Method method = (Method) entity;
      final Class<?> clazz = method.getDeclaringClass();
      final String methodName = method.getName();
      final String className = clazz.getSimpleName();
      name = String.format("%s.%s", className, methodName);
    }

    return name;
  }

  @Override
  public Span onOperation(final Span span, final Object entity) {

    assert span != null;
    if (entity != null) {
      final String name = entityName(entity);
      if (name != null) {
        span.setOperationName(name);
      }
    }
    return span;
  }
}
