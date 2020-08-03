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

package io.opentelemetry.instrumentation.armeria.v1_0.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Route;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Decorates an {@link HttpService} to trace inbound {@link HttpRequest}s. */
public class OpenTelemetryService extends SimpleDecoratingHttpService {

  /** Creates a new tracing {@link HttpService} decorator using the default {@link Tracer}. */
  public static Function<? super HttpService, OpenTelemetryService> newDecorator() {
    return newDecorator(new ArmeriaServerTracer());
  }

  /** Creates a new tracing {@link HttpService} decorator using the specified {@link Tracer}. */
  public static Function<? super HttpService, OpenTelemetryService> newDecorator(Tracer tracer) {
    return newDecorator(new ArmeriaServerTracer(tracer));
  }

  /** Creates a new tracing {@link HttpService} decorator using the specified {@link Tracer}. */
  public static Function<? super HttpService, OpenTelemetryService> newDecorator(
      ArmeriaServerTracer serverTracer) {
    return new Decorator(serverTracer);
  }

  private final ArmeriaServerTracer serverTracer;

  private OpenTelemetryService(HttpService delegate, ArmeriaServerTracer serverTracer) {
    super(delegate);
    this.serverTracer = serverTracer;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    String route = route(ctx);
    String spanName = route != null ? route : "HTTP " + req.method().name();

    // Always available in practice.
    long requestStartTimeMicros =
        ctx.log().ensureAvailable(RequestLogProperty.REQUEST_START_TIME).requestStartTimeMicros();
    long requestStartTimeNanos = TimeUnit.MICROSECONDS.toNanos(requestStartTimeMicros);
    Span span = serverTracer.startSpan(req, ctx, spanName, requestStartTimeNanos);

    if (span.isRecording()) {
      ctx.log()
          .whenComplete()
          .thenAccept(
              log -> {
                long requestEndTimeNanos = requestStartTimeNanos + log.responseDurationNanos();
                if (log.responseCause() != null) {
                  serverTracer.endExceptionally(
                      span, log.responseCause(), log, requestEndTimeNanos);
                } else {
                  serverTracer.end(span, log, requestEndTimeNanos);
                }
              });
    }

    try (Scope ignored = TracingContextUtils.currentContextWith(span)) {
      return unwrap().serve(ctx, req);
    }
  }

  private static String route(ServiceRequestContext ctx) {
    final Route route = ctx.config().route();
    final List<String> paths = route.paths();
    switch (route.pathType()) {
      case EXACT:
      case PREFIX:
      case PARAMETERIZED:
        return paths.get(1);
      case REGEX:
        return paths.get(paths.size() - 1);
      case REGEX_WITH_PREFIX:
        return paths.get(1) + paths.get(0);
    }
    return null;
  }

  private static class Decorator implements Function<HttpService, OpenTelemetryService> {

    private final ArmeriaServerTracer serverTracer;

    private Decorator(ArmeriaServerTracer serverTracer) {
      this.serverTracer = serverTracer;
    }

    @Override
    public OpenTelemetryService apply(HttpService httpService) {
      return new OpenTelemetryService(httpService, serverTracer);
    }
  }
}
