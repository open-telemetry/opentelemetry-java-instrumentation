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
import com.linecorp.armeria.common.logging.RequestLogProperty;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.util.concurrent.TimeUnit;

/** Decorates an {@link HttpClient} to trace outbound {@link HttpResponse}s. */
final class OpenTelemetryClient extends SimpleDecoratingHttpClient {

  private final ArmeriaClientTracer clientTracer;

  OpenTelemetryClient(HttpClient delegate, ArmeriaClientTracer clientTracer) {
    super(delegate);
    this.clientTracer = clientTracer;
  }

  @Override
  public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
    // Always available in practice.
    long requestStartTimeMicros =
        ctx.log().ensureAvailable(RequestLogProperty.REQUEST_START_TIME).requestStartTimeMicros();
    long requestStartTimeNanos = TimeUnit.MICROSECONDS.toNanos(requestStartTimeMicros);
    Context context = clientTracer.startSpan(Context.current(), ctx, ctx, requestStartTimeNanos);

    Span span = Span.fromContext(context);
    if (span.isRecording()) {
      ctx.log()
          .whenComplete()
          .thenAccept(
              log -> {
                NetPeerUtils.INSTANCE.setNetPeer(span, ctx.remoteAddress());

                long requestEndTimeNanos = requestStartTimeNanos + log.responseDurationNanos();
                if (log.responseCause() != null) {
                  clientTracer.endExceptionally(
                      context, log, log.responseCause(), requestEndTimeNanos);
                } else {
                  clientTracer.end(context, log, requestEndTimeNanos);
                }
              });
    }

    try (Scope ignored = context.makeCurrent()) {
      return unwrap().execute(ctx, req);
    }
  }
}
