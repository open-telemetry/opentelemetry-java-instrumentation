/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public abstract class CompletionListener<T> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.spymemcached.experimental-span-attributes", false);

  private static final String DB_COMMAND_CANCELLED = "spymemcached.command.cancelled";
  private static final String MEMCACHED_RESULT = "spymemcached.result";
  private static final String HIT = "hit";
  private static final String MISS = "miss";

  private final Context context;
  private final SpymemcachedRequest request;

  protected CompletionListener(Context parentContext, SpymemcachedRequest request) {
    this.request = request;
    context = instrumenter().start(parentContext, request);
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
        instrumenter().end(context, request, null, e);
      }
    } catch (InterruptedException e) {
      // Avoid swallowing InterruptedException
      instrumenter().end(context, request, null, e);
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      // This should never happen, just in case to make sure we cover all unexpected exceptions
      instrumenter().end(context, request, null, t);
    } finally {
      instrumenter().end(context, request, future, null);
    }
  }

  protected void closeSyncSpan(Throwable thrown) {
    instrumenter().end(context, request, null, thrown);
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(Span span, boolean hit) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(MEMCACHED_RESULT, hit ? HIT : MISS);
    }
  }
}
