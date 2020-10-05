/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.hibernate;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HibernateDecorator extends ClientDecorator {
  public static final HibernateDecorator DECORATE = new HibernateDecorator();
  // TODO use tracer names *.hibernate-3.3, *.hibernate-4.0, *.hibernate-4.3 respectively in each
  // module
  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.hibernate");

  public String spanNameForOperation(String operationName, Object entity) {
    if (entity != null) {
      String entityName = entityName(entity);
      if (entityName != null) {
        return operationName + " " + entityName;
      }
    }
    return operationName;
  }

  public String entityName(Object entity) {
    if (entity == null) {
      return null;
    }
    String name = null;
    Set<String> annotations = new HashSet<>();
    for (Annotation annotation : entity.getClass().getDeclaredAnnotations()) {
      annotations.add(annotation.annotationType().getName());
    }

    if (entity instanceof String) {
      // We were given an entity name, not the entity itself.
      name = (String) entity;
    } else if (annotations.contains("javax.persistence.Entity")) {
      // We were given an instance of an entity.
      name = entity.getClass().getName();
    } else if (entity instanceof List && !((List) entity).isEmpty()) {
      // We have a list of entities.
      name = entityName(((List) entity).get(0));
    }

    return name;
  }
}
