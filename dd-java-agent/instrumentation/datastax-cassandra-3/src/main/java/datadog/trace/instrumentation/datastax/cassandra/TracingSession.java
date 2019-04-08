package datadog.trace.instrumentation.datastax.cassandra;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static datadog.trace.instrumentation.datastax.cassandra.CassandraClientDecorator.DECORATE;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TracingSession implements Session {

  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final Session session;
  private final Tracer tracer;

  public TracingSession(final Session session, final Tracer tracer) {
    this.session = session;
    this.tracer = tracer;
  }

  @Override
  public String getLoggedKeyspace() {
    return session.getLoggedKeyspace();
  }

  @Override
  public Session init() {
    return new TracingSession(session.init(), tracer);
  }

  @Override
  public ListenableFuture<Session> initAsync() {
    return Futures.transform(
        session.initAsync(),
        new Function<Session, Session>() {
          @Override
          public Session apply(final Session session) {
            return new TracingSession(session, tracer);
          }
        },
        directExecutor());
  }

  @Override
  public ResultSet execute(final String query) {
    try (final Scope scope = startSpanWithScope(query)) {
      try {
        final ResultSet resultSet = session.execute(query);
        beforeSpanFinish(scope.span(), resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(scope.span(), e);
        throw e;
      } finally {
        scope.span().finish();
      }
    }
  }

  @Override
  public ResultSet execute(final String query, final Object... values) {
    try (final Scope scope = startSpanWithScope(query)) {
      try {
        final ResultSet resultSet = session.execute(query, values);
        beforeSpanFinish(scope.span(), resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(scope.span(), e);
        throw e;
      } finally {
        scope.span().finish();
      }
    }
  }

  @Override
  public ResultSet execute(final String query, final Map<String, Object> values) {
    try (final Scope scope = startSpanWithScope(query)) {
      try {
        final ResultSet resultSet = session.execute(query, values);
        beforeSpanFinish(scope.span(), resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(scope.span(), e);
        throw e;
      } finally {
        scope.span().finish();
      }
    }
  }

  @Override
  public ResultSet execute(final Statement statement) {
    final String query = getQuery(statement);
    try (final Scope scope = startSpanWithScope(query)) {
      try {
        final ResultSet resultSet = session.execute(statement);
        beforeSpanFinish(scope.span(), resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(scope.span(), e);
        throw e;
      } finally {
        scope.span().finish();
      }
    }
  }

  @Override
  public ResultSetFuture executeAsync(final String query) {
    try (final Scope scope = startSpanWithScope(query)) {
      final ResultSetFuture future = session.executeAsync(query);
      future.addListener(createListener(scope.span(), future), executorService);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(final String query, final Object... values) {
    try (final Scope scope = startSpanWithScope(query)) {
      final ResultSetFuture future = session.executeAsync(query, values);
      future.addListener(createListener(scope.span(), future), executorService);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(final String query, final Map<String, Object> values) {
    try (final Scope scope = startSpanWithScope(query)) {
      final ResultSetFuture future = session.executeAsync(query, values);
      future.addListener(createListener(scope.span(), future), executorService);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(final Statement statement) {
    final String query = getQuery(statement);
    try (final Scope scope = startSpanWithScope(query)) {
      final ResultSetFuture future = session.executeAsync(statement);
      future.addListener(createListener(scope.span(), future), executorService);

      return future;
    }
  }

  @Override
  public PreparedStatement prepare(final String query) {
    return session.prepare(query);
  }

  @Override
  public PreparedStatement prepare(final RegularStatement statement) {
    return session.prepare(statement);
  }

  @Override
  public ListenableFuture<PreparedStatement> prepareAsync(final String query) {
    return session.prepareAsync(query);
  }

  @Override
  public ListenableFuture<PreparedStatement> prepareAsync(final RegularStatement statement) {
    return session.prepareAsync(statement);
  }

  @Override
  public CloseFuture closeAsync() {
    return session.closeAsync();
  }

  @Override
  public void close() {
    session.close();
  }

  @Override
  public boolean isClosed() {
    return session.isClosed();
  }

  @Override
  public Cluster getCluster() {
    return session.getCluster();
  }

  @Override
  public State getState() {
    return session.getState();
  }

  private static String getQuery(final Statement statement) {
    String query = null;
    if (statement instanceof BoundStatement) {
      query = ((BoundStatement) statement).preparedStatement().getQueryString();
    } else if (statement instanceof RegularStatement) {
      query = ((RegularStatement) statement).getQueryString();
    }

    return query == null ? "" : query;
  }

  private static Runnable createListener(final Span span, final ResultSetFuture future) {
    return new Runnable() {
      @Override
      public void run() {
        try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
          beforeSpanFinish(span, future.get());
        } catch (final InterruptedException | ExecutionException e) {
          beforeSpanFinish(span, e);
        } finally {
          span.finish();
        }
      }
    };
  }

  private Scope startSpanWithScope(final String query) {
    final Span span = tracer.buildSpan("cassandra.execute").start();
    final Scope scope = tracer.scopeManager().activate(span, false);
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, session);
    DECORATE.onStatement(span, query);
    return scope;
  }

  private static void beforeSpanFinish(final Span span, final ResultSet resultSet) {
    DECORATE.onResponse(span, resultSet);
    DECORATE.beforeFinish(span);
  }

  private static void beforeSpanFinish(final Span span, final Exception e) {
    DECORATE.onError(span, e);
    DECORATE.beforeFinish(span);
  }
}
