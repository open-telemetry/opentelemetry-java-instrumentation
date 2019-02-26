package datadog.trace.agent.decorator;

import datadog.trace.api.DDTags;
import io.opentracing.Span;

public abstract class OrmClientDecorator extends DatabaseClientDecorator {

  public abstract <ENTITY> String entityName(final ENTITY entity);

  public <ENTITY> Span onOperation(final Span span, final ENTITY entity) {

    assert span != null;
    if (entity != null) {
      final String name = entityName(entity);
      if (name != null) {
        span.setTag(DDTags.RESOURCE_NAME, name);
      } // else we keep any existing resource.
    }
    return span;
  }
}
