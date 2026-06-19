/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1.internal;

import io.lettuce.core.protocol.CommandWrapper;
import io.lettuce.core.protocol.RedisCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * Javaagent bridge for aggregating commands written while Lettuce auto-flush is disabled.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LettuceBatchSupport {
  private static final Map<Object, BatchState> batchStates =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<RedisCommand<?, ?, ?>, BatchScope> activeBatchCommands =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final ThreadLocal<BatchScope> currentBatchScope = new ThreadLocal<>();

  public static void setAutoFlushCommands(Object commands, boolean autoFlush) {
    if (autoFlush) {
      batchStates.remove(commands);
    } else {
      batchStates.put(commands, new BatchState());
    }
  }

  public static void capture(Object commands, RedisCommand<?, ?, ?> command) {
    BatchState batchState = batchStates.get(commands);
    if (batchState != null) {
      batchState.capture(command);
    }
  }

  @Nullable
  public static BatchScope startBatch(Object commands) {
    BatchState batchState = batchStates.get(commands);
    if (batchState == null) {
      return null;
    }
    return batchState.start();
  }

  public static void finishBatch(BatchScope batch, @Nullable Throwable throwable) {
    currentBatchScope.remove();
    if (throwable != null) {
      batch.finish(throwable);
    }
  }

  public static void startCommand(RedisCommand<?, ?, ?> command) {
    BatchScope batchScope = activeBatchCommands.get(command);
    if (batchScope == null) {
      batchScope = activeBatchCommands.get(CommandWrapper.unwrap(command));
    }
    if (batchScope != null) {
      currentBatchScope.set(batchScope);
    }
  }

  public static void endCommand() {
    currentBatchScope.remove();
  }

  @Nullable
  static BatchScope captureCurrent(OpenTelemetryTracing.OpenTelemetrySpan span) {
    BatchScope batchScope = currentBatchScope.get();
    if (batchScope == null) {
      return null;
    }
    batchScope.capture(span);
    return batchScope;
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

  /**
   * Active Lettuce batch span scope.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class BatchScope {
    private final List<RedisCommand<?, ?, ?>> commands;
    private final AtomicInteger remaining;
    @Nullable private OpenTelemetryTracing.OpenTelemetrySpan aggregateSpan;
    @Nullable private Throwable error;
    @Nullable private String errorMessage;

    private BatchScope(List<RedisCommand<?, ?, ?>> commands) {
      this.commands = commands;
      this.remaining = new AtomicInteger(commands.size());
      for (RedisCommand<?, ?, ?> command : commands) {
        activeBatchCommands.put(command, this);
      }
    }

    private synchronized void capture(OpenTelemetryTracing.OpenTelemetrySpan span) {
      if (aggregateSpan == null) {
        aggregateSpan = span.createAggregateSpan(commands);
      }
    }

    synchronized void finishOne(OpenTelemetryTracing.OpenTelemetrySpan span) {
      if (span.getBatchErrorMessage() != null && errorMessage == null) {
        errorMessage = span.getBatchErrorMessage();
      }
      if (span.getError() != null && error == null) {
        error = span.getError();
      }

      if (remaining.getAndDecrement() == 1) {
        finish(null);
      }
    }

    private synchronized void finish(@Nullable Throwable throwable) {
      OpenTelemetryTracing.OpenTelemetrySpan span = aggregateSpan;
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
      span.finishWithResponse(errorMessage, error);
    }
  }

  private LettuceBatchSupport() {}
}
