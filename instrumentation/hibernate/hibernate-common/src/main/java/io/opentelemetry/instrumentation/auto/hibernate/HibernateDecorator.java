/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.hibernate;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.OrmClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HibernateDecorator extends OrmClientDecorator {
  public static final HibernateDecorator DECORATE = new HibernateDecorator();
  // TODO use tracer names *.hibernate-3.3, *.hibernate-4.0, *.hibernate-4.3 respectively in each
  // module
  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.hibernate");

  @Override
  protected String dbSystem() {
    return null;
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbName(final Object o) {
    return null;
  }

  @Override
  public String entityName(final Object entity) {
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
