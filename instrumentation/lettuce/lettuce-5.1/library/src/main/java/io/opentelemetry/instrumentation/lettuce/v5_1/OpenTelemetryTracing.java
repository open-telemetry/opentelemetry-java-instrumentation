/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CompleteableCommand;
import io.lettuce.core.protocol.OtelCommandArgsUtil;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class OpenTelemetryTracing implements Tracing {

  private final TracerProvider tracerProvider;

  OpenTelemetryTracing(
      Instrumenter<LettuceRequest, Void> instrumenter,
      RedisCommandSanitizer sanitizer,
      boolean encodingEventsEnabled) {
    this.tracerProvider =
        new OpenTelemetryTracerProvider(instrumenter, sanitizer, encodingEventsEnabled);
  }

  @Override
  public TracerProvider getTracerProvider() {
    return tracerProvider;
  }

  @Override
  public TraceContextProvider initialTraceContextProvider() {
    return new OpenTelemetryTraceContextProvider();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  // Added in lettuce 5.2, ignored in 6.0+
  // @Override
  public boolean includeCommandArgsInSpanTags() {
    return true;
  }

  @Override
  @Nullable
  public Endpoint createEndpoint(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      return new OpenTelemetryEndpoint((InetSocketAddress) socketAddress);
    }
    return null;
  }

  private static class OpenTelemetryTracerProvider implements TracerProvider {

    private final Tracer openTelemetryTracer;

    OpenTelemetryTracerProvider(
        Instrumenter<LettuceRequest, Void> instrumenter,
        RedisCommandSanitizer sanitizer,
        boolean encodingEventsEnabled) {
      openTelemetryTracer = new OpenTelemetryTracer(instrumenter, sanitizer, encodingEventsEnabled);
    }

    @Override
    public Tracer getTracer() {
      return openTelemetryTracer;
    }
  }

  private static class OpenTelemetryTraceContextProvider implements TraceContextProvider {

    @Override
    public TraceContext getTraceContext() {
      return new OpenTelemetryTraceContext();
    }
  }

  private static class OpenTelemetryTraceContext implements TraceContext {
    private final Context context;

    OpenTelemetryTraceContext() {
      this.context = Context.current();
    }

    Context getContext() {
      return context;
    }
  }

  static class OpenTelemetryEndpoint implements Endpoint {
    @Nullable final InetSocketAddress address;

    OpenTelemetryEndpoint(@Nullable InetSocketAddress address) {
      this.address = address;
    }
  }

  private static class OpenTelemetryTracer extends Tracer {

    private final Instrumenter<LettuceRequest, Void> instrumenter;
    private final RedisCommandSanitizer sanitizer;
    private final boolean encodingEventsEnabled;

    OpenTelemetryTracer(
        Instrumenter<LettuceRequest, Void> instrumenter,
        RedisCommandSanitizer sanitizer,
        boolean encodingEventsEnabled) {
      this.instrumenter = instrumenter;
      this.sanitizer = sanitizer;
      this.encodingEventsEnabled = encodingEventsEnabled;
    }

    @Override
    public OpenTelemetrySpan nextSpan() {
      return nextSpan(Context.current());
    }

    @Override
    public OpenTelemetrySpan nextSpan(TraceContext traceContext) {
      if (!(traceContext instanceof OpenTelemetryTraceContext)) {
        return nextSpan();
      }

      Context context = ((OpenTelemetryTraceContext) traceContext).getContext();
      return nextSpan(context);
    }

    private OpenTelemetrySpan nextSpan(Context parentContext) {
      return new OpenTelemetrySpan(parentContext, instrumenter, sanitizer, encodingEventsEnabled);
    }
  }

  // The order that callbacks will be called in or which thread they are called from is not well
  // defined. We go ahead and buffer all data until we know we have a span. This implementation is
  // particularly safe, synchronizing all accesses. Relying on implementation details would allow
  // reducing synchronization but the impact should be minimal.
  private static class OpenTelemetrySpan extends Tracer.Span {

    private final Context parentContext;
    private final Instrumenter<LettuceRequest, Void> instrumenter;
    private final boolean encodingEventsEnabled;
    private final LettuceRequest request;

    @Nullable private List<Object> events;
    @Nullable private Throwable error;
    @Nullable private Context context;

    OpenTelemetrySpan(
        Context parentContext,
        Instrumenter<LettuceRequest, Void> instrumenter,
        RedisCommandSanitizer sanitizer,
        boolean encodingEventsEnabled) {
      this.parentContext = parentContext;
      this.instrumenter = instrumenter;
      this.encodingEventsEnabled = encodingEventsEnabled;
      this.request = new LettuceRequest(sanitizer);
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span name(String name) {
      request.setCommand(name);

      if (context != null) {
        Span.fromContext(context).updateName(name);
      }

      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span remoteEndpoint(Endpoint endpoint) {
      if (endpoint instanceof OpenTelemetryEndpoint) {
        OpenTelemetryEndpoint openTelemetryEndpoint = (OpenTelemetryEndpoint) endpoint;
        if (openTelemetryEndpoint.address != null) {
          request.setAddress(openTelemetryEndpoint.address);
        }
      }
      return this;
    }

    // Added and called in 6.0+
    // @Override
    @CanIgnoreReturnValue
    @SuppressWarnings({"UnusedMethod", "EffectivelyPrivate"})
    public synchronized Tracer.Span start(RedisCommand<?, ?, ?> command) {
      // Extract args BEFORE calling start() so db.query.text can include them
      // Extract args BEFORE calling start() so db.statement can include them
      if (command.getArgs() != null) {
        request.setArgsList(OtelCommandArgsUtil.getCommandArgs(command.getArgs()));
      }

      start();

      Context currentContext = context;
      if (currentContext == null) {
        return this;
      }

      Span span = Span.fromContext(currentContext);
      span.updateName(command.getType().toString());

      if (command instanceof CompleteableCommand) {
        CompleteableCommand<?> completeableCommand = (CompleteableCommand<?>) command;
        completeableCommand.onComplete(
            (o, throwable) -> {
              if (throwable != null) {
                span.recordException(throwable);
              }

              CommandOutput<?, ?, ?> output = command.getOutput();
              if (output != null) {
                String errorMsg = output.getError();
                if (errorMsg != null) {
                  span.setStatus(StatusCode.ERROR);
                }
              }

              finish();
            });
      }

      return this;
    }

    // Not called by Lettuce in 6.0+ (though we call it ourselves above).
    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span start() {
      if (instrumenter.shouldStart(parentContext, request)) {
        context = instrumenter.start(parentContext, request);
        Span span = Span.fromContext(context);

        // Update span name if command was set before start
        if (request.getCommand() != null) {
          span.updateName(request.getCommand());
        }

        // Apply buffered events
        if (events != null) {
          for (int i = 0; i < events.size(); i += 2) {
            span.addEvent((String) events.get(i), (Instant) events.get(i + 1));
          }
          events = null;
        }

        // Apply buffered error
        if (error != null) {
          span.setStatus(StatusCode.ERROR);
          span.recordException(error);
          error = null;
        }
      }

      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span annotate(String value) {
      if (!encodingEventsEnabled && value.startsWith("redis.encode.")) {
        // skip noisy encode events produced by io.lettuce.core.protocol.TracedCommand
        return this;
      }

      if (context != null) {
        Span.fromContext(context).addEvent(value);
      } else {
        if (events == null) {
          events = new ArrayList<>();
        }
        events.add(value);
        events.add(Instant.now());
      }
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span tag(String key, String value) {
      if (value == null || value.isEmpty()) {
        return this;
      }
      if (key.equals("redis.args")) {
        request.setArgsString(value);
        return this;
      }
      if (key.equals("db.namespace")) {
        try {
          request.setDatabaseIndex(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
          // ignore invalid values
        }
        return this;
      }
      if (context != null) {
        Span.fromContext(context).setAttribute(key, value);
      }
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span error(Throwable throwable) {
      this.error = throwable;
      return this;
    }

    @Override
    public synchronized void finish() {
      if (context != null) {
        instrumenter.end(context, request, null, error);
      }
    }
  }
}
