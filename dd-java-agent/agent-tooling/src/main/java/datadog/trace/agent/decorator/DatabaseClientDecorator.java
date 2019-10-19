package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentSpan;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  @Deprecated
  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    Tags.DB_TYPE.set(span, dbType());
    return super.afterStart(span);
  }

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setTag(Tags.DB_TYPE.getKey(), dbType());
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
  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      Tags.DB_USER.set(span, dbUser(connection));
      final String instanceName = dbInstance(connection);
      Tags.DB_INSTANCE.set(span, instanceName);

      if (instanceName != null && Config.get().isDbClientSplitByInstance()) {
        span.setTag(DDTags.SERVICE_NAME, instanceName);
      }
    }
    return span;
  }

  /**
   * This should be called when the connection is being used, not when it's created.
   *
   * @param span
   * @param connection
   * @return
   */
  public AgentSpan onConnection(final AgentSpan span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setTag(Tags.DB_USER.getKey(), dbUser(connection));
      final String instanceName = dbInstance(connection);
      span.setTag(Tags.DB_INSTANCE.getKey(), instanceName);

      if (instanceName != null && Config.get().isDbClientSplitByInstance()) {
        span.setTag(DDTags.SERVICE_NAME, instanceName);
      }
    }
    return span;
  }

  @Deprecated
  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    Tags.DB_STATEMENT.set(span, statement);
    return span;
  }

  public AgentSpan onStatement(final AgentSpan span, final String statement) {
    assert span != null;
    span.setTag(Tags.DB_STATEMENT.getKey(), statement);
    return span;
  }
}
