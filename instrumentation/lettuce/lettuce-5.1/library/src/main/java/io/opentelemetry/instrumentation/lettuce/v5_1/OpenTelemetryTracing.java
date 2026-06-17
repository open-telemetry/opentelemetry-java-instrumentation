/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CommandWrapper;
import io.lettuce.core.protocol.CompleteableCommand;
import io.lettuce.core.protocol.OtelCommandArgsUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

final class OpenTelemetryTracing implements Tracing {
  private static final Map<Object, BatchState> batchStates =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<RedisCommand<?, ?, ?>, BatchScope> activeBatchCommands =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final ThreadLocal<BatchScope> currentBatchScope = new ThreadLocal<>();

  private final TracerProvider tracerProvider;

  static void setAutoFlushCommands(Object commands, boolean autoFlush) {
    if (autoFlush) {
      batchStates.remove(commands);
    } else {
      batchStates.put(commands, new BatchState());
    }
  }

  static void capture(Object commands, RedisCommand<?, ?, ?> command) {
    BatchState batchState = batchStates.get(commands);
    if (batchState != null) {
      batchState.capture(command);
    }
  }

  @Nullable
  static Object startBatch(Object commands) {
    BatchState batchState = batchStates.get(commands);
    if (batchState == null) {
      return null;
    }
    BatchScope batchScope = batchState.start();
    return batchScope;
  }

  static void finishBatch(Object batch, @Nullable Throwable throwable) {
    currentBatchScope.remove();
    if (throwable != null && batch instanceof BatchScope) {
      ((BatchScope) batch).finish(throwable);
    }
  }

  static void startCommand(RedisCommand<?, ?, ?> command) {
    BatchScope batchScope = activeBatchCommands.get(command);
    if (batchScope == null) {
      batchScope = activeBatchCommands.get(CommandWrapper.unwrap(command));
    }
    if (batchScope != null) {
      currentBatchScope.set(batchScope);
    }
  }

  static void endCommand() {
    currentBatchScope.remove();
  }

  OpenTelemetryTracing(
      Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
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
        Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
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
    private final boolean encodingEventsEnabled;

    OpenTelemetryTracer(
        Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
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

  private static final class BatchState {
    private final List<RedisCommand<?, ?, ?>> commands = new ArrayList<>();

    private synchronized void capture(RedisCommand<?, ?, ?> command) {
      commands.add(command);
    }

    @Nullable
    private synchronized BatchScope start() {
      if (commands.isEmpty()) {
        return null;
      }

      List<RedisCommand<?, ?, ?>> batchCommands = new ArrayList<>(commands);
      commands.clear();
      return new BatchScope(batchCommands);
    }
  }

  private static final class BatchScope {
    private final List<RedisCommand<?, ?, ?>> commands;
    private final AtomicInteger remaining;
    @Nullable private OpenTelemetrySpan aggregateSpan;
    @Nullable private Throwable error;
    @Nullable private String errorMessage;

    private BatchScope(List<RedisCommand<?, ?, ?>> commands) {
      this.commands = commands;
      this.remaining = new AtomicInteger(commands.size());
      for (RedisCommand<?, ?, ?> command : commands) {
        activeBatchCommands.put(command, this);
      }
    }

    private synchronized boolean capture(OpenTelemetrySpan span) {
      if (aggregateSpan == null) {
        aggregateSpan = span.createAggregateSpan(commands);
      }
      span.batchScope = this;
      return true;
    }

    private synchronized void finishOne(OpenTelemetrySpan span) {
      LettuceResponse response = span.response;
      if (response != null && response.getErrorMessage() != null && errorMessage == null) {
        errorMessage = response.getErrorMessage();
      }
      if (span.errorMessage != null && errorMessage == null) {
        errorMessage = span.errorMessage;
      }
      if (span.error != null && error == null) {
        error = span.error;
      }

      if (remaining.getAndDecrement() == 1) {
        finish(null);
      }
    }

    private synchronized void finish(@Nullable Throwable throwable) {
      OpenTelemetrySpan span = aggregateSpan;
      if (span == null) {
        return;
      }
      aggregateSpan = null;
      for (RedisCommand<?, ?, ?> command : commands) {
        activeBatchCommands.remove(command);
      }
      if (throwable != null) {
        error = throwable;
      }
      span.finishWithResponse(new LettuceResponse(errorMessage, error));
    }
  }

  // The order that callbacks will be called in or which thread they are called from is not well
  // defined. We go ahead and buffer all data until we know we have a span. This implementation is
  // particularly safe, synchronizing all accesses. Relying on implementation details would allow
  // reducing synchronization but the impact should be minimal.
  private static class OpenTelemetrySpan extends Tracer.Span {

    private final Context parentContext;
    private final Instrumenter<LettuceRequest, LettuceResponse> instrumenter;
    private final RedisCommandSanitizer sanitizer;
    private final boolean encodingEventsEnabled;
    private final LettuceRequest request;

    @Nullable private List<Object> events;
    @Nullable private Throwable error;
    @Nullable private String errorMessage;
    @Nullable private LettuceResponse response;
    @Nullable private Context context;
    @Nullable private BatchScope batchScope;
    private boolean aggregate;

    OpenTelemetrySpan(
        Context parentContext,
        Instrumenter<LettuceRequest, LettuceResponse> instrumenter,
        RedisCommandSanitizer sanitizer,
        boolean encodingEventsEnabled) {
      this.parentContext = parentContext;
      this.instrumenter = instrumenter;
      this.sanitizer = sanitizer;
      this.encodingEventsEnabled = encodingEventsEnabled;
      this.request = new LettuceRequest(sanitizer);
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span name(String name) {
      request.setCommand(name);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span remoteEndpoint(Endpoint endpoint) {
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
    public synchronized Tracer.Span start(RedisCommand<?, ?, ?> command) {
      // Extract args BEFORE calling start() so db.query.text can include them
      if (command.getArgs() != null) {
        request.setArgsList(OtelCommandArgsUtil.getCommandArgs(command.getArgs()));
      }

      BatchScope batchScope = aggregate ? null : currentBatchScope.get();
      if (batchScope != null && batchScope.capture(this)) {
        if (command instanceof CompleteableCommand) {
          CompleteableCommand<?> completeableCommand = (CompleteableCommand<?>) command;
          completeableCommand.onComplete(
              (o, throwable) -> {
                CommandOutput<?, ?, ?> output = command.getOutput();
                String errorMsg = output != null ? output.getError() : null;
                finishWithResponse(new LettuceResponse(errorMsg, throwable));
              });
        }
        return this;
      }

      start();

      if (context == null) {
        return this;
      }

      if (command instanceof CompleteableCommand) {
        CompleteableCommand<?> completeableCommand = (CompleteableCommand<?>) command;
        completeableCommand.onComplete(
            (o, throwable) -> {
              CommandOutput<?, ?, ?> output = command.getOutput();
              String errorMsg = output != null ? output.getError() : null;
              finishWithResponse(new LettuceResponse(errorMsg, throwable));
            });
      }

      return this;
    }

    // Not called by Lettuce in 6.0+ (though we call it ourselves above).
    @Override
    @CanIgnoreReturnValue
    public synchronized Tracer.Span start() {
      BatchScope batchScope = aggregate ? null : currentBatchScope.get();
      if (batchScope != null && batchScope.capture(this)) {
        return this;
      }

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

    private OpenTelemetrySpan createAggregateSpan(List<RedisCommand<?, ?, ?>> commands) {
      OpenTelemetrySpan span =
          new OpenTelemetrySpan(parentContext, instrumenter, sanitizer, encodingEventsEnabled);
      span.aggregate = true;
      span.request.setPipeline(commands);
      InetSocketAddress address = request.getAddress();
      if (address != null) {
        span.request.setAddress(address);
      }
      Long databaseIndex = request.getDatabaseIndex();
      if (databaseIndex != null) {
        span.request.setDatabaseIndex(databaseIndex);
      }
      span.start();
      return span;
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
      if (key.equals("error")) {
        errorMessage = value;
        return this;
      }
      // Under old semconv forward unknown tags as raw span attributes for backward compatibility;
      // under stable semconv these are either captured structurally (e.g. error.type) or not needed
      if (emitOldDatabaseSemconv() && context != null) {
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

    private synchronized void finishWithResponse(LettuceResponse resp) {
      this.response = resp;
      if (this.error == null && resp.getThrowable() != null) {
        this.error = resp.getThrowable();
      }
      finish();
    }

    @Override
    public synchronized void finish() {
      BatchScope batchScope = this.batchScope;
      if (batchScope != null) {
        this.batchScope = null;
        batchScope.finishOne(this);
        return;
      }
      if (context != null) {
        instrumenter.end(context, request, response, error);
        // Null out context to prevent double-ending if both the onComplete callback and Lettuce's
        // direct finish() call execute.
        context = null;
      }
    }
  }
}
