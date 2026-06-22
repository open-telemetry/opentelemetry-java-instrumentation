/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.batchInstrumenter;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

public final class LettuceBatchContext {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .getBoolean("experimental_span_attributes/development", false);

  private static final Map<Object, BatchState> states =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static void setCollecting(Object commands, boolean collecting) {
    if (collecting) {
      states.put(commands, new BatchState());
    } else {
      states.remove(commands);
    }
  }

  public static boolean isCollecting(Object commands) {
    return states.containsKey(commands);
  }

  public static boolean capture(
      Object commands,
      RedisCommand<?, ?, ?> command,
      @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
    BatchState state = states.get(commands);
    if (state == null) {
      return false;
    }
    state.add(command, asyncCommand);
    return true;
  }

  @Nullable
  public static BatchScope start(Object commands) {
    BatchState state = states.get(commands);
    return state == null ? null : state.start();
  }

  private LettuceBatchContext() {}

  public static final class BatchScope {
    private final Context context;
    private final LettuceBatchRequest request;
    private final AtomicInteger remaining;
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    private BatchScope(Context context, LettuceBatchRequest request, int remaining) {
      this.context = context;
      this.request = request;
      this.remaining = new AtomicInteger(remaining);
    }

    @Nullable
    private static BatchScope start(
        List<RedisCommand<?, ?, ?>> commands,
        List<AsyncCommand<?, ?, ?>> asyncCommands,
        @Nullable Context capturedParentContext) {
      LettuceBatchRequest request = LettuceBatchRequest.create(commands);
      Context parentContext =
          capturedParentContext == null ? currentContext() : capturedParentContext;
      if (!batchInstrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = batchInstrumenter().start(parentContext, request);
      if (asyncCommands.isEmpty()) {
        batchInstrumenter().end(context, request, null, null);
        return null;
      }
      BatchScope scope = new BatchScope(context, request, asyncCommands.size());
      for (AsyncCommand<?, ?, ?> asyncCommand : asyncCommands) {
        asyncCommand.handleAsync(
            (value, throwable) -> {
              scope.endOne(throwable);
              return null;
            });
      }
      return scope;
    }

    public void endOne(@Nullable Throwable throwable) {
      if (throwable instanceof CancellationException) {
        if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
          Span.fromContext(context).setAttribute("lettuce.command.cancelled", true);
        }
        throwable = null;
      }
      if (throwable != null) {
        error.compareAndSet(null, throwable);
      }
      if (remaining.getAndDecrement() == 1) {
        batchInstrumenter().end(context, request, null, error.get());
      }
    }
  }

  private static final class BatchState {
    private final List<RedisCommand<?, ?, ?>> commands = new ArrayList<>();
    private final List<AsyncCommand<?, ?, ?>> asyncCommands = new ArrayList<>();
    @Nullable private Context parentContext;

    // Lettuce documents manual flushing as reliable only for single-threaded use or with external
    // synchronization, so this state follows the same assumption.
    private void add(RedisCommand<?, ?, ?> command, @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
      commands.add(command);
      if (parentContext == null && asyncCommand != null) {
        parentContext = CONTEXT.get(asyncCommand);
      }
      if (asyncCommand != null && LettuceInstrumentationUtil.expectsResponse(command)) {
        asyncCommands.add(asyncCommand);
      }
    }

    @Nullable
    private BatchScope start() {
      if (commands.isEmpty()) {
        return null;
      }

      List<RedisCommand<?, ?, ?>> batchCommands = new ArrayList<>(commands);
      commands.clear();
      List<AsyncCommand<?, ?, ?>> batchAsyncCommands = new ArrayList<>(asyncCommands);
      asyncCommands.clear();
      Context batchParentContext = parentContext;
      parentContext = null;
      return BatchScope.start(batchCommands, batchAsyncCommands, batchParentContext);
    }
  }
}
