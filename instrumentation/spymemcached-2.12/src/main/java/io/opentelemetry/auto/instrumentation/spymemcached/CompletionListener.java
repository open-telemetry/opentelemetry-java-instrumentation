/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.spymemcached;

import static io.opentelemetry.auto.instrumentation.spymemcached.MemcacheClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.spymemcached.MemcacheClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.spy.memcached.MemcachedConnection;

@Slf4j
public abstract class CompletionListener<T> {

  static final String SERVICE_NAME = "memcached";
  static final String COMPONENT_NAME = "java-spymemcached";
  static final String DB_TYPE = "memcached";
  static final String DB_COMMAND_CANCELLED = "db.command.cancelled";
  static final String MEMCACHED_RESULT = "memcaced.result";
  static final String HIT = "hit";
  static final String MISS = "miss";

  private final Span span;

  public CompletionListener(final MemcachedConnection connection, final String methodName) {
    span =
        TRACER
            .spanBuilder(DECORATE.spanNameOnOperation(methodName))
            .setSpanKind(CLIENT)
            .startSpan();
    try (final Scope scope = currentContextWith(span)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, connection);
    }
  }

  protected void closeAsyncSpan(final T future) {
    try (final Scope scope = currentContextWith(span)) {
      try {
        processResult(span, future);
      } catch (final CancellationException e) {
        span.setAttribute(DB_COMMAND_CANCELLED, true);
      } catch (final ExecutionException e) {
        if (e.getCause() instanceof CancellationException) {
          // Looks like underlying OperationFuture wraps CancellationException into
          // ExecutionException
          span.setAttribute(DB_COMMAND_CANCELLED, true);
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
        span.end();
      }
    }
  }

  protected void closeSyncSpan(final Throwable thrown) {
    try (final Scope scope = currentContextWith(span)) {
      DECORATE.onError(span, thrown);
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(final Span span, final boolean hit) {
    span.setAttribute(MEMCACHED_RESULT, hit ? HIT : MISS);
  }
}
