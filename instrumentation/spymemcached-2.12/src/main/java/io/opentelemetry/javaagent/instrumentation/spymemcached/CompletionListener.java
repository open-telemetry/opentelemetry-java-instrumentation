/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.MemcacheClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;

public abstract class CompletionListener<T> {

  static final String DB_COMMAND_CANCELLED = "db.command.cancelled";
  static final String MEMCACHED_RESULT = "memcaced.result";
  static final String HIT = "hit";
  static final String MISS = "miss";

  private final Span span;

  public CompletionListener(MemcachedConnection connection, String methodName) {
    span = tracer().startSpan(connection, methodName);
  }

  protected void closeAsyncSpan(T future) {
    try {
      processResult(span, future);
    } catch (CancellationException e) {
      span.setAttribute(DB_COMMAND_CANCELLED, true);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CancellationException) {
        // Looks like underlying OperationFuture wraps CancellationException into
        // ExecutionException
        span.setAttribute(DB_COMMAND_CANCELLED, true);
      } else {
        tracer().endExceptionally(span, e);
      }
    } catch (InterruptedException e) {
      // Avoid swallowing InterruptedException
      tracer().endExceptionally(span, e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      // This should never happen, just in case to make sure we cover all unexpected exceptions
      tracer().endExceptionally(span, e);
    } finally {
      tracer().end(span);
    }
  }

  protected void closeSyncSpan(Throwable thrown) {
    tracer().endExceptionally(span, thrown);
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(Span span, boolean hit) {
    span.setAttribute(MEMCACHED_RESULT, hit ? HIT : MISS);
  }
}
