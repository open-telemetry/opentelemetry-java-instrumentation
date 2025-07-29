/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Decorates an {@link HttpService} to trace inbound {@link HttpRequest}s. */
final class OpenTelemetryService extends SimpleDecoratingHttpService {

  private final Instrumenter<ServiceRequestContext, RequestLog> instrumenter;

  OpenTelemetryService(
      HttpService delegate, Instrumenter<ServiceRequestContext, RequestLog> instrumenter) {
    super(delegate);
    this.instrumenter = instrumenter;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, ctx)) {
      return unwrap().serve(ctx, req);
    }

    Context context = instrumenter.start(parentContext, ctx);

    ctx.log()
        .whenComplete()
        .thenAccept(log -> instrumenter.end(context, ctx, log, log.responseCause()));

    try (Scope ignored = context.makeCurrent()) {
      return unwrap().serve(ctx, req);
    }
  }
}
