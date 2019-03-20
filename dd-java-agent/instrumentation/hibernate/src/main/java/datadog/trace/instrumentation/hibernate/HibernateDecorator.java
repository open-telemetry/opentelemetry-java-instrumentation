package datadog.trace.instrumentation.hibernate;

import datadog.trace.agent.decorator.OrmClientDecorator;
import datadog.trace.api.DDSpanTypes;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HibernateDecorator extends OrmClientDecorator {
  public static final HibernateDecorator DECORATOR = new HibernateDecorator();

  @Override
  protected String service() {
    return "hibernate";
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"hibernate-core"};
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.HIBERNATE;
  }

  @Override
  protected String component() {
    return "java-hibernate";
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
    final Set<String> annotations = new HashSet<>();
    for (final Annotation annotation : entity.getClass().getDeclaredAnnotations()) {
      annotations.add(annotation.annotationType().getName());
    }

    if (entity instanceof String) {
      // We were given an entity name, not the entity itself.
      name = (String) entity;
    } else if (annotations.contains("javax.persistence.Entity")) {
      // We were given an instance of an entity.
      name = entity.getClass().getName();
    } else if (entity instanceof List && ((List) entity).size() > 0) {
      // We have a list of entities.
      name = entityName(((List) entity).get(0));
    }

    return name;
  }
}
