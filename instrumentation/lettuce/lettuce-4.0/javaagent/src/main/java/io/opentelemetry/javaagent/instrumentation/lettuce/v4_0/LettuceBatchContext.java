/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.batchInstrumenter;

import com.lambdaworks.redis.AbstractRedisAsyncCommands;
import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

// Lettuce documents manual flushing as reliable only for single-threaded use or with external
// synchronization, so the batch state tracked here follows the same assumption and is not
// synchronized.
public final class LettuceBatchContext {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .getBoolean("experimental_span_attributes/development", false);

  private static final VirtualField<AbstractRedisAsyncCommands<?, ?>, BatchState> BATCH_STATE =
      VirtualField.find(AbstractRedisAsyncCommands.class, BatchState.class);

  public static void setBatching(AbstractRedisAsyncCommands<?, ?> commands, boolean batching) {
    BATCH_STATE.set(commands, batching ? new BatchState() : null);
  }

  public static boolean isBatching(AbstractRedisAsyncCommands<?, ?> commands) {
    return BATCH_STATE.get(commands) != null;
  }

  public static boolean capture(
      AbstractRedisAsyncCommands<?, ?> commands,
      RedisCommand<?, ?, ?> command,
      @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
    BatchState state = BATCH_STATE.get(commands);
    if (state == null) {
      return false;
    }
    state.add(command, asyncCommand);
    return true;
  }

  @Nullable
  public static BatchScope flush(AbstractRedisAsyncCommands<?, ?> commands) {
    BatchState state = BATCH_STATE.get(commands);
    if (state == null || state.isEmpty()) {
      return null;
    }
    // flushCommands() does not re-enable auto-flush, so keep batching active with a fresh buffer
    BATCH_STATE.set(commands, new BatchState());
    return BatchScope.start(state.commands, state.asyncCommands, state.parentContext);
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
      // Redis executes batch commands in order, but the individual async command futures can
      // complete in a different order. Observe every future so an earlier failure or cancellation
      // is captured; the remaining counter decides when all responses have completed.
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
          // On a batch span this means at least one command was cancelled
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

    private void add(RedisCommand<?, ?, ?> command, @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
      commands.add(command);
      if (parentContext == null && asyncCommand != null) {
        parentContext = CONTEXT.get(asyncCommand);
      }
      if (asyncCommand != null && InstrumentationPoints.expectsResponse(command)) {
        asyncCommands.add(asyncCommand);
      }
    }

    private boolean isEmpty() {
      return commands.isEmpty();
    }
  }
}
