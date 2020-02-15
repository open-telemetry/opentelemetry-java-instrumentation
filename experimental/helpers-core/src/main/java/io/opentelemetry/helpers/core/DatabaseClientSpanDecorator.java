package io.opentelemetry.helpers.core;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/**
 * Abstract base span decorator implementation for database client-side spans.
 *
 * @param <C> the context propagation carrier type
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public abstract class DatabaseClientSpanDecorator<C, Q, P> extends ClientSpanDecorator<C, Q, P> {

  /**
   * Constructs a span decorator object.
   *
   * @param tracer the tracer to use in recording spans
   * @param contextManager the context manager to use in handling correlation contexts
   * @param meter the meter to use in recoding measurements
   * @param propagationSetter the decorator-specific context propagation setter
   */
  protected DatabaseClientSpanDecorator(
      Tracer tracer,
      DistributedContextManager contextManager,
      Meter meter,
      Setter<C> propagationSetter) {
    super(tracer, contextManager, meter, propagationSetter);
  }

  protected abstract DbInfo getDbInfo();

  @Override
  protected void addSpanAttributes(Span span, C carrier, Q inbound) {
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.DB_TYPE, getDbInfo().getDbType());
    putAttributeIfNotEmptyOrNull(
        span, SemanticConventions.DB_INSTANCE, getDbInfo().getDbInstance());
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.DB_USER, getDbInfo().getDbUser());
    putAttributeIfNotEmptyOrNull(span, SemanticConventions.DB_URL, getDbInfo().getDbUrl());
  }

  @Override
  protected void addResultSpanAttributes(Span span, Throwable throwable, P outbound) {}
}
