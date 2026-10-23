/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.CommandWrapper;
import io.lettuce.core.protocol.CompleteableCommand;
import io.lettuce.core.protocol.OtelCommandArgsUtil;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.tracing.TraceContext;
import io.lettuce.core.tracing.TraceContextProvider;
import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.TracerProvider;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.trace.Span;
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
      Instrumenter<LettuceRequest, LettuceResponse> instrumenter, RedisCommandSanitizer sanitizer) {
    this.tracerProvider = new OpenTelemetryTracerProvider(instrumenter, sanitizer);
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
        Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
        RedisCommandSanitizer sanitizer) {
      openTelemetryTracer = new OpenTelemetryTracer(instrumenter, sanitizer);
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

    private Context getContext() {
      return context;
    }
  }

  private static class OpenTelemetryEndpoint implements Endpoint {
    private final InetSocketAddress address;

    private OpenTelemetryEndpoint(InetSocketAddress address) {
      this.address = address;
    }
  }

  private static class OpenTelemetryTracer extends Tracer {

    private final Instrumenter<LettuceRequest, LettuceResponse> instrumenter;
    private final RedisCommandSanitizer sanitizer;

    OpenTelemetryTracer(
        Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
        RedisCommandSanitizer sanitizer) {
      this.instrumenter = instrumenter;
      this.sanitizer = sanitizer;
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
      return new OpenTelemetrySpan(parentContext, instrumenter, sanitizer);
    }
  }

  // Callback registration publishes initialized span state to the completing thread.
  // Each completion callback runs once, and encoding annotations do not access span state.
  private static class OpenTelemetrySpan extends Tracer.Span {

    private final Context parentContext;
    private final Instrumenter<LettuceRequest, LettuceResponse> instrumenter;
    private final LettuceRequest request;

    @Nullable private List<Object> events;
    @Nullable private Throwable error;
    @Nullable private String taggedErrorMessage;
    @Nullable private Context context;

    OpenTelemetrySpan(
        Context parentContext,
        Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
        RedisCommandSanitizer sanitizer) {
      this.parentContext = parentContext;
      this.instrumenter = instrumenter;
      this.request = new LettuceRequest(sanitizer);
    }

    @Override
    @CanIgnoreReturnValue
    public Tracer.Span name(String name) {
      request.setCommand(name);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Tracer.Span remoteEndpoint(Endpoint endpoint) {
      if (endpoint instanceof OpenTelemetryEndpoint) {
        OpenTelemetryEndpoint openTelemetryEndpoint = (OpenTelemetryEndpoint) endpoint;
        request.setAddress(openTelemetryEndpoint.address);
      }
      return this;
    }

    // Added and called in 6.0+
    // @Override
    @CanIgnoreReturnValue
    @SuppressWarnings({"UnusedMethod", "EffectivelyPrivate"})
    public Tracer.Span start(RedisCommand<?, ?, ?> command) {
      // In Lettuce 6.0.0-6.0.2 the command name is missing when start() runs,
      // so take name from the command. Later versions already set the name correctly,
      // so only fill it in when it is still null.
      // Use toString(): name() was removed from ProtocolKeyword in 6.5.0+.
      ProtocolKeyword type = command.getType();
      if (request.getCommand() == null && type != null) {
        request.setCommand(type.toString());
      }

      // Extract args BEFORE calling start() so db.query.text can include them
      if (command.getArgs() != null) {
        request.setArgsList(OtelCommandArgsUtil.getCommandArgs(command.getArgs()));
      }

      start();

      if (context == null) {
        return this;
      }

      if (command instanceof CompleteableCommand) {
        CompleteableCommand<?> completeableCommand = (CompleteableCommand<?>) command;
        AsyncCommand<?, ?, ?> asyncCommand = CommandWrapper.unwrap(command, AsyncCommand.class);
        completeableCommand.onComplete(
            (o, throwable) -> {
              Throwable error = throwable != null ? throwable : this.error;
              // CommandWrapper can report null even when its async delegate failed.
              if (asyncCommand != null && asyncCommand.isCompletedExceptionally()) {
                asyncCommand.whenComplete(
                    (result, terminalError) ->
                        finishFromOutput(
                            command.getOutput(), terminalError != null ? terminalError : error));
              } else {
                finishFromOutput(command.getOutput(), error);
              }
            });
      }

      return this;
    }

    // Not called by Lettuce in 6.0+ (though we call it ourselves above).
    @Override
    @CanIgnoreReturnValue
    public Tracer.Span start() {
      if (instrumenter.shouldStart(parentContext, request)) {
        context = instrumenter.start(parentContext, request);

        // Apply buffered events
        if (events != null) {
          Span span = Span.fromContext(context);
          for (int i = 0; i < events.size(); i += 2) {
            span.addEvent((String) events.get(i), (Instant) events.get(i + 1));
          }
          events = null;
        }
      }

      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Tracer.Span annotate(String value) {
      if (value.startsWith("redis.encode.")) {
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
    public Tracer.Span tag(String key, String value) {
      if (key.equals("error")) {
        taggedErrorMessage = value;
        return this;
      }
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
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public Tracer.Span error(Throwable throwable) {
      this.error = throwable;
      return this;
    }

    @Override
    public void finish() {
      if (error != null) {
        end(null, error);
      } else {
        // Lettuce 5.x tags both server and client errors here, suppressing their throwable.
        end(taggedErrorMessage != null ? LettuceResponse.unknownFailure() : null, null);
      }
    }

    private void finishFromOutput(
        @Nullable CommandOutput<?, ?, ?> output, @Nullable Throwable error) {
      if (error != null) {
        end(null, error);
      } else {
        end(LettuceResponse.fromCommandOutput(output), null);
      }
    }

    private void end(@Nullable LettuceResponse response, @Nullable Throwable error) {
      Context context = this.context;
      if (context != null) {
        this.context = null;
        instrumenter.end(context, request, response, error);
      }
    }
  }
}
