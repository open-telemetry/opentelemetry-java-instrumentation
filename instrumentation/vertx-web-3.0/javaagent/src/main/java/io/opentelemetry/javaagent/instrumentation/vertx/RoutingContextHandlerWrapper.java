/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This is used to wrap Vert.x Handlers to provide nice user-friendly SERVER span names */
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {

  private static final Logger log = LoggerFactory.getLogger(RoutingContextHandlerWrapper.class);

  private final Handler<RoutingContext> handler;

  public RoutingContextHandlerWrapper(Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext context) {
    try {
      Span serverSpan = ServerSpan.fromContextOrNull(Context.current());
      if (serverSpan != null) {
        // TODO should update only SERVER span using
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/465
        serverSpan.updateName(context.currentRoute().getPath());
      }
    } catch (Exception ex) {
      log.error("Failed to update server span name with vert.x route", ex);
    }
    handler.handle(context);
  }
}
