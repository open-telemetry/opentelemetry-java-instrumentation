/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.TimeUnit;

/** Decorates an {@link HttpService} to trace inbound {@link HttpRequest}s. */
final class OpenTelemetryService extends SimpleDecoratingHttpService {

  private final ArmeriaServerTracer serverTracer;

  OpenTelemetryService(HttpService delegate, ArmeriaServerTracer serverTracer) {
    super(delegate);
    this.serverTracer = serverTracer;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    String spanName = ctx.config().route().patternString();

    // Always available in practice.
    long requestStartTimeMicros =
        ctx.log().ensureAvailable(RequestLogProperty.REQUEST_START_TIME).requestStartTimeMicros();
    long requestStartTimeNanos = TimeUnit.MICROSECONDS.toNanos(requestStartTimeMicros);
    Context context = serverTracer.startSpan(req, ctx, null, spanName, requestStartTimeNanos);

    Span span = Span.fromContext(context);
    if (span.isRecording()) {
      ctx.log()
          .whenComplete()
          .thenAccept(
              log -> {
                if (log.responseHeaders().status() == HttpStatus.NOT_FOUND) {
                  // Assume a not-found request was not served. The route we use by default will be
                  // some fallback like `/*` which is not as useful as the requested path.
                  span.updateName(ctx.path());
                }
                long requestEndTimeNanos = requestStartTimeNanos + log.responseDurationNanos();
                if (log.responseCause() != null) {
                  serverTracer.endExceptionally(
                      context, log.responseCause(), log, requestEndTimeNanos);
                } else {
                  serverTracer.end(context, log, requestEndTimeNanos);
                }
              });
    }

    try (Scope ignored = context.makeCurrent()) {
      return unwrap().serve(ctx, req);
    }
  }
}
