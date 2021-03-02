/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.MemcacheClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import net.spy.memcached.MemcachedConnection;

public abstract class CompletionListener<T> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty(
              "otel.instrumentation.spymemcached.experimental-span-attributes", false);

  private static final String DB_COMMAND_CANCELLED = "spymemcached.command.cancelled";
  private static final String MEMCACHED_RESULT = "spymemcached.result";
  private static final String HIT = "hit";
  private static final String MISS = "miss";

  private final Context context;

  public CompletionListener(
      Context parentContext, MemcachedConnection connection, String methodName) {
    context = tracer().startSpan(parentContext, connection, methodName);
  }

  protected void closeAsyncSpan(T future) {
    Span span = Span.fromContext(context);
    try {
      processResult(span, future);
    } catch (CancellationException e) {
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        span.setAttribute(DB_COMMAND_CANCELLED, true);
      }
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CancellationException) {
        // Looks like underlying OperationFuture wraps CancellationException into
        // ExecutionException
        if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
          span.setAttribute(DB_COMMAND_CANCELLED, true);
        }
      } else {
        tracer().endExceptionally(context, e);
      }
    } catch (InterruptedException e) {
      // Avoid swallowing InterruptedException
      tracer().endExceptionally(context, e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      // This should never happen, just in case to make sure we cover all unexpected exceptions
      tracer().endExceptionally(context, e);
    } finally {
      tracer().end(context);
    }
  }

  protected void closeSyncSpan(Throwable thrown) {
    if (thrown == null) {
      tracer().end(context);
    } else {
      tracer().endExceptionally(context, thrown);
    }
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(Span span, boolean hit) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(MEMCACHED_RESULT, hit ? HIT : MISS);
    }
  }
}
