/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Decorates an {@link HttpClient} to trace outbound {@link HttpResponse}s. */
public class OpenTelemetryClient extends SimpleDecoratingHttpClient {

  /** Creates a new tracing {@link HttpClient} decorator using the default {@link Tracer}. */
  public static OpenTelemetryClient.Decorator newDecorator() {
    return new Decorator(new ArmeriaClientTracer());
  }

  /** Creates a new tracing {@link HttpClient} decorator using the specified {@link Tracer}. */
  public static OpenTelemetryClient.Decorator newDecorator(Tracer tracer) {
    return new Decorator(new ArmeriaClientTracer(tracer));
  }

  /**
   * Creates a new tracing {@link HttpClient} decorator using the specified {@link
   * ArmeriaClientTracer}.
   */
  public static OpenTelemetryClient.Decorator newDecorator(ArmeriaClientTracer clientTracer) {
    return new Decorator(clientTracer);
  }

  private final ArmeriaClientTracer clientTracer;

  private OpenTelemetryClient(HttpClient delegate, ArmeriaClientTracer clientTracer) {
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

  private static class Decorator implements Function<HttpClient, OpenTelemetryClient> {

    private final ArmeriaClientTracer clientTracer;

    private Decorator(ArmeriaClientTracer clientTracer) {
      this.clientTracer = clientTracer;
    }

    @Override
    public OpenTelemetryClient apply(HttpClient httpClient) {
      return new OpenTelemetryClient(httpClient, clientTracer);
    }
  }
}
