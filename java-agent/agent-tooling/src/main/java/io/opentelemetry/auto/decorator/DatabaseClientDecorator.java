package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;

public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  @Deprecated
  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setAttribute(Tags.DB_TYPE, dbType());
    return super.afterStart(span);
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    span.setAttribute(Tags.DB_TYPE, dbType());
    return super.afterStart(span);
  }

  /**
   * This should be called when the connection is being used, not when it's created.
   *
   * @param span
   * @param connection
   * @return
   */
  @Deprecated
  public AgentSpan onConnection(final AgentSpan span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setAttribute(Tags.DB_USER, dbUser(connection));
      final String instanceName = dbInstance(connection);
      span.setAttribute(Tags.DB_INSTANCE, instanceName);

      if (instanceName != null && Config.get().isDbClientSplitByInstance()) {
        span.setAttribute(MoreTags.SERVICE_NAME, instanceName);
      }
    }
    return span;
  }

  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      final String user = dbUser(connection);
      if (user != null) {
        span.setAttribute(Tags.DB_USER, user);
      }
      final String instanceName = dbInstance(connection);
      if (instanceName != null) {
        span.setAttribute(Tags.DB_INSTANCE, instanceName);
      }

      if (instanceName != null && Config.get().isDbClientSplitByInstance()) {
        span.setAttribute(MoreTags.SERVICE_NAME, instanceName);
      }
    }
    return span;
  }

  @Deprecated
  public AgentSpan onStatement(final AgentSpan span, final String statement) {
    assert span != null;
    span.setAttribute(Tags.DB_STATEMENT, statement);
    return span;
  }

  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    span.setAttribute(Tags.DB_STATEMENT, statement);
    return span;
  }
}
