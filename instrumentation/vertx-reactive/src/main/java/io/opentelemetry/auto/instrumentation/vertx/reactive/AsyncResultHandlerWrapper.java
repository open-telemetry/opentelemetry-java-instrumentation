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
package io.opentelemetry.auto.instrumentation.vertx.reactive;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncResultHandlerWrapper implements Handler<Handler<AsyncResult<?>>> {
  private final Handler<Handler<AsyncResult<?>>> delegate;
  private final Span parentSpan;

  public AsyncResultHandlerWrapper(
      final Handler<Handler<AsyncResult<?>>> delegate, Span parentSpan) {
    this.delegate = delegate;
    this.parentSpan = parentSpan;
  }

  @Override
  public void handle(final Handler<AsyncResult<?>> asyncResultHandler) {
    if (parentSpan != null) {
      try (final Scope scope = VertxDecorator.TRACER.withSpan(parentSpan)) {
        delegate.handle(asyncResultHandler);
      }
    } else {
      delegate.handle(asyncResultHandler);
    }
  }

  public static Handler<Handler<AsyncResult<?>>> wrapIfNeeded(
      final Handler<Handler<AsyncResult<?>>> delegate, final Span parentSpan) {
    if (!(delegate instanceof AsyncResultHandlerWrapper)) {
      log.debug("Wrapping handler {}", delegate);
      return new AsyncResultHandlerWrapper(delegate, parentSpan);
    }
    return delegate;
  }
}
