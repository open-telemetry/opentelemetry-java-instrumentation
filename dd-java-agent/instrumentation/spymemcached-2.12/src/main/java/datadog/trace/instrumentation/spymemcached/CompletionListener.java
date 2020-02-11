package datadog.trace.instrumentation.spymemcached;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.spymemcached.MemcacheClientDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.spy.memcached.MemcachedConnection;

@Slf4j
public abstract class CompletionListener<T> {

  // Note: it looks like this value is being ignored and DBTypeDecorator overwrites it.
  static final String OPERATION_NAME = "memcached.query";

  static final String SERVICE_NAME = "memcached";
  static final String COMPONENT_NAME = "java-spymemcached";
  static final String DB_TYPE = "memcached";
  static final String DB_COMMAND_CANCELLED = "db.command.cancelled";
  static final String MEMCACHED_RESULT = "memcaced.result";
  static final String HIT = "hit";
  static final String MISS = "miss";

  private final MemcachedConnection connection;
  private final AgentSpan span;

  public CompletionListener(final MemcachedConnection connection, final String methodName) {
    this.connection = connection;
    span = startSpan(OPERATION_NAME);
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, connection);
      DECORATE.onOperation(span, methodName);
    }
  }

  protected void closeAsyncSpan(final T future) {
    try (final AgentScope scope = activateSpan(span, false)) {
      try {
        processResult(span, future);
      } catch (final CancellationException e) {
        span.setTag(DB_COMMAND_CANCELLED, true);
      } catch (final ExecutionException e) {
        if (e.getCause() instanceof CancellationException) {
          // Looks like underlying OperationFuture wraps CancellationException into
          // ExecutionException
          span.setTag(DB_COMMAND_CANCELLED, true);
        } else {
          DECORATE.onError(span, e.getCause());
        }
      } catch (final InterruptedException e) {
        // Avoid swallowing InterruptedException
        DECORATE.onError(span, e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        // This should never happen, just in case to make sure we cover all unexpected exceptions
        DECORATE.onError(span, e);
      } finally {
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }

  protected void closeSyncSpan(final Throwable thrown) {
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.onError(span, thrown);
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  protected abstract void processResult(AgentSpan span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(final AgentSpan span, final boolean hit) {
    span.setTag(MEMCACHED_RESULT, hit ? HIT : MISS);
  }
}
