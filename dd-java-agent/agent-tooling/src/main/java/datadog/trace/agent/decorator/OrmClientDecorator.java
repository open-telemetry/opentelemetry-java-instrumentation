package datadog.trace.agent.decorator;

import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentSpan;

public abstract class OrmClientDecorator extends DatabaseClientDecorator {

  public abstract String entityName(final Object entity);

  public AgentSpan onOperation(final AgentSpan span, final Object entity) {

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
