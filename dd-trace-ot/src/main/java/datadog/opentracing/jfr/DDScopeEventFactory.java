package datadog.opentracing.jfr;

import datadog.opentracing.DDSpanContext;

/** Factory that produces scope events */
public interface DDScopeEventFactory {

  /**
   * Create new scope event for given context.
   *
   * @param context span context.
   * @return scope event instance
   */
  DDScopeEvent create(final DDSpanContext context);
}
