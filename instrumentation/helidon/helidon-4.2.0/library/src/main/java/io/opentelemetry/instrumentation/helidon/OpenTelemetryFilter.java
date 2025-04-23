/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon;

import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

final class OpenTelemetryFilter implements Filter {

  private final Instrumenter<ServerRequest, ServerResponse> instrumenter;

  OpenTelemetryFilter(Instrumenter<ServerRequest, ServerResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, req)) {
      chain.proceed();
      return;
    }

    Context context = instrumenter.start(parentContext, req);

    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      chain.proceed();
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(context, req, res, error);
    }
  }
}
