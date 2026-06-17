/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.batchInstrumenter;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
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
    if (state == null || state.commands.isEmpty()) {
      return null;
    }
    return BatchScope.start(state.drainCommands(), state.drainAsyncCommands(), state.parentContext);
  }

  private LettuceBatchContext() {}

  public static final class BatchScope {
    private final Context context;
    private final LettuceBatchRequest request;
    private final AtomicInteger remaining;

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
      if (remaining.getAndDecrement() == 1) {
        batchInstrumenter().end(context, request, null, throwable);
      }
    }
  }

  private static final class BatchState {
    private final List<RedisCommand<?, ?, ?>> commands = new ArrayList<>();
    private final List<AsyncCommand<?, ?, ?>> asyncCommands = new ArrayList<>();
    @Nullable private Context parentContext;

    private void add(RedisCommand<?, ?, ?> command, @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
      commands.add(command);
      if (parentContext == null && asyncCommand != null) {
        parentContext = CONTEXT.get(asyncCommand);
      }
      if (asyncCommand != null && InstrumentationPoints.expectsResponse(command)) {
        asyncCommands.add(asyncCommand);
      }
    }

    private List<RedisCommand<?, ?, ?>> drainCommands() {
      List<RedisCommand<?, ?, ?>> drainedCommands = new ArrayList<>(commands);
      commands.clear();
      return drainedCommands;
    }

    private List<AsyncCommand<?, ?, ?>> drainAsyncCommands() {
      List<AsyncCommand<?, ?, ?>> drainedAsyncCommands = new ArrayList<>(asyncCommands);
      asyncCommands.clear();
      return drainedAsyncCommands;
    }
  }
}
