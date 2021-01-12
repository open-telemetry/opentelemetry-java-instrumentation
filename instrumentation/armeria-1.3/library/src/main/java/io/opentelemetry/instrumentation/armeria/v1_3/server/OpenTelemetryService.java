/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.RequestLogProperty;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Route;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

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

  /**
   * Creates a new tracing {@link HttpService} decorator using the specified {@link
   * ArmeriaServerTracer}.
   */
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
    Context context = serverTracer.startSpan(req, ctx, null, spanName, requestStartTimeNanos);

    if (Span.fromContext(context).isRecording()) {
      ctx.log()
          .whenComplete()
          .thenAccept(
              log -> {
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

  @Nullable
  private static String route(ServiceRequestContext ctx) {
    Route route = ctx.config().route();
    List<String> paths = route.paths();
    switch (route.pathType()) {
      case EXACT:
      case PREFIX:
      case PARAMETERIZED:
        return paths.get(1);
      case REGEX:
        return paths.get(paths.size() - 1);
      case REGEX_WITH_PREFIX:
        return paths.get(1) + paths.get(0);
      default:
        return null;
    }
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
