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

package io.opentelemetry.auto.instrumentation.vertx;

import static io.opentelemetry.auto.instrumentation.vertx.VertxDecorator.TRACER;

import io.opentelemetry.trace.Span;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/** This is used to wrap Vert.x Handlers to provide nice user-friendly SERVER span names */
@Slf4j
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {
  private final Handler<RoutingContext> handler;

  public RoutingContextHandlerWrapper(final Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext context) {
    try {
      Span currentSpan = TRACER.getCurrentSpan();
      if (currentSpan.getContext().isValid()) {
        // TODO should update only SERVER span using
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/465
        currentSpan.updateName(context.currentRoute().getPath());
      }
    } catch (Exception ex) {
      log.error("Failed to update server span name with vert.x route", ex);
    }
    handler.handle(context);
  }
}
