package datadog.trace.bootstrap.instrumentation.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;

public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setTag(Tags.DB_TYPE, dbType());
    return super.afterStart(span);
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
      span.setTag(Tags.DB_USER, dbUser(connection));
      final String instanceName = dbInstance(connection);
      span.setTag(Tags.DB_INSTANCE, instanceName);

      if (instanceName != null && Config.get().isDbClientSplitByInstance()) {
        span.setTag(DDTags.SERVICE_NAME, instanceName);
      }
    }
    return span;
  }

  public AgentSpan onStatement(final AgentSpan span, final String statement) {
    assert span != null;
    span.setTag(Tags.DB_STATEMENT, statement);
    return span;
  }
}
