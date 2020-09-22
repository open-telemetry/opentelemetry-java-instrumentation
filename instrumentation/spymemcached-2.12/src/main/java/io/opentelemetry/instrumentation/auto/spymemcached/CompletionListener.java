/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.spymemcached;

import static io.opentelemetry.instrumentation.auto.spymemcached.MemcacheClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
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
    try (Scope ignored = currentContextWith(span)) {
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
