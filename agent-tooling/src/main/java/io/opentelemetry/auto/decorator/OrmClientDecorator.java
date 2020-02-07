package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;

public abstract class OrmClientDecorator extends DatabaseClientDecorator {

  public abstract String entityName(final Object entity);

  public Span onOperation(final Span span, final Object entity) {

    assert span != null;
    if (entity != null) {
      final String name = entityName(entity);
      if (name != null) {
        span.setAttribute(MoreTags.RESOURCE_NAME, name);
      } // else we keep any existing resource.
    }
    return span;
  }
}
