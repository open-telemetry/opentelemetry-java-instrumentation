/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spymemcached;

import static io.opentelemetry.instrumentation.auto.spymemcached.MemcacheClientTracer.TRACER;

import io.opentelemetry.trace.Span;
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
    span = TRACER.startSpan(connection, methodName);
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
        TRACER.endExceptionally(span, e);
      }
    } catch (InterruptedException e) {
      // Avoid swallowing InterruptedException
      TRACER.endExceptionally(span, e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      // This should never happen, just in case to make sure we cover all unexpected exceptions
      TRACER.endExceptionally(span, e);
    } finally {
      TRACER.end(span);
    }
  }

  protected void closeSyncSpan(Throwable thrown) {
    TRACER.endExceptionally(span, thrown);
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(Span span, boolean hit) {
    span.setAttribute(MEMCACHED_RESULT, hit ? HIT : MISS);
  }
}
