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

package io.opentelemetry.instrumentation.armeria.v1_0.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseTracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
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
    Span span = clientTracer.startSpan(ctx, requestStartTimeNanos);

    if (span.isRecording()) {
      ctx.log()
          .whenComplete()
          .thenAccept(
              log -> {
                BaseTracer.onPeerConnection(span, ctx.remoteAddress());

                long requestEndTimeNanos = requestStartTimeNanos + log.responseDurationNanos();
                if (log.responseCause() != null) {
                  clientTracer.endExceptionally(
                      span, log, log.responseCause(), requestEndTimeNanos);
                } else {
                  clientTracer.end(span, log, requestEndTimeNanos);
                }
              });
    }

    try (Scope ignored = clientTracer.startScope(span, ctx)) {
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
