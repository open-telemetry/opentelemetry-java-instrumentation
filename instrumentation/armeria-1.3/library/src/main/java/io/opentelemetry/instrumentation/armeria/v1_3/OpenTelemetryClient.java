/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.TimeUnit;

/** Decorates an {@link HttpClient} to trace outbound {@link HttpResponse}s. */
final class OpenTelemetryClient extends SimpleDecoratingHttpClient {

  private final Instrumenter<ClientRequestContext, RequestLog> instrumenter;

  OpenTelemetryClient(
      HttpClient delegate, Instrumenter<ClientRequestContext, RequestLog> instrumenter) {
    super(delegate);
    this.instrumenter = instrumenter;
  }

  @Override
  public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, ctx)) {
      return unwrap().execute(ctx, req);
    }

    // Always available in practice.
    long requestStartTimeMicros =
        ctx.log().ensureAvailable(RequestLogProperty.REQUEST_START_TIME).requestStartTimeMicros();
    long requestStartTimeNanos = TimeUnit.MICROSECONDS.toNanos(requestStartTimeMicros);
    Context context = instrumenter.start(Context.current(), ctx);

    Span span = Span.fromContext(context);
    if (span.isRecording()) {
      ctx.log()
          .whenComplete()
          .thenAccept(
              log -> {
                long requestEndTimeNanos = requestStartTimeNanos + log.responseDurationNanos();
                instrumenter.end(context, ctx, log, log.responseCause());
              });
    }

    try (Scope ignored = context.makeCurrent()) {
      return unwrap().execute(ctx, req);
    }
  }
}
