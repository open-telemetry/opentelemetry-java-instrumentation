package datadog.trace.instrumentation.hibernate;

import datadog.trace.agent.decorator.OrmClientDecorator;
import datadog.trace.api.DDSpanTypes;
import java.util.List;
import javax.persistence.Entity;

public class HibernateDecorator extends OrmClientDecorator {
  public static final HibernateDecorator INSTANCE = new HibernateDecorator();

  @Override
  protected String service() {
    return "hibernate";
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"hibernate"};
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
  public <ENTITY> String entityName(final ENTITY entity) {
    String name = null;
    if (entity instanceof String) {
      // We were given an entity name, not the entity itself.
      name = (String) entity;
    } else if (entity.getClass().isAnnotationPresent(Entity.class)) {
      // We were given an instance of an entity.
      name = entity.getClass().getName();
    } else if (entity instanceof List && ((List) entity).size() > 0) {
      // We have a list of entities.
      name = entityName(((List) entity).get(0));
    }
    return name;
  }
}
