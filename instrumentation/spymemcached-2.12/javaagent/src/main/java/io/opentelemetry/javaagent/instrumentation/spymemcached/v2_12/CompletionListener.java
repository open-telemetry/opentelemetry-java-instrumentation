/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedSingletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public abstract class CompletionListener<T> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "spymemcached")
          .getBoolean("experimental_span_attributes/development", false);

  private static final String DB_COMMAND_CANCELLED = "spymemcached.command.cancelled";
  private static final String MEMCACHED_RESULT = "spymemcached.result";
  private static final String HIT = "hit";
  private static final String MISS = "miss";
  private static final Logger logger = Logger.getLogger(CompletionListener.class.getName());

  private final SpymemcachedRequest request;
  private final Context context;

  protected CompletionListener(Context parentContext, SpymemcachedRequest request) {
    this.request = request;
    context = instrumenter().start(parentContext, request);
  }

  public Context getContext() {
    return context;
  }

  protected void closeAsyncSpan(T future) {
    Span span = Span.fromContext(context);
    Throwable error = null;
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
        error = e;
      }
    } catch (InterruptedException e) {
      // Avoid swallowing InterruptedException
      error = e;
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      // This should never happen, just in case to make sure we cover all unexpected exceptions
      error = t;
    } finally {
      endSpan(future, error);
    }
  }

  protected void closeSyncSpan(Throwable thrown) {
    endSpan(null, thrown);
  }

  private void endSpan(@Nullable T response, @Nullable Throwable error) {
    try {
      request.captureHandlingNodeAddress();
    } catch (Throwable t) {
      // Keep completion isolated from a custom MemcachedNode address getter.
      logger.log(FINE, "Unable to capture the Memcached peer address", t);
    } finally {
      instrumenter().end(context, request, response, error);
    }
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(Span span, boolean hit) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(MEMCACHED_RESULT, hit ? HIT : MISS);
    }
  }

  public void done(Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
