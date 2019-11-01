package datadog.opentracing.scopemanager;

import io.opentracing.Scope;
import io.opentracing.Span;

// Intentionally package private.
interface DDScope extends Scope {
  @Override
  Span span();
}
