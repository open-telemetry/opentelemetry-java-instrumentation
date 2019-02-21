package datadog.trace.agent.decorator;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class DatabaseClientDecorator<SESSION> extends ClientDecorator {

  protected abstract String dbType();

  protected abstract String dbUser(SESSION session);

  protected abstract String dbInstance(SESSION session);

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    Tags.DB_TYPE.set(span, dbType());
    return super.afterStart(span);
  }

  public Span onSession(final Span span, final SESSION statement) {
    assert span != null;
    Tags.DB_USER.set(span, dbUser(statement));
    Tags.DB_INSTANCE.set(span, dbInstance(statement));
    return span;
  }

  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    Tags.DB_STATEMENT.set(span, statement);
    return span;
  }
}
