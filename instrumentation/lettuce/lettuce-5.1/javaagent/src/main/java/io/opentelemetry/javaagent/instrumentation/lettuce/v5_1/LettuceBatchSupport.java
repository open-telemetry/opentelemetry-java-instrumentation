/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.RedisChannelWriter;
import io.lettuce.core.protocol.CommandWrapper;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.lettuce.v5_1.internal.LettuceBatching;
import io.opentelemetry.instrumentation.lettuce.v5_1.internal.LettuceBatching.BatchSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

/**
 * Agent-side aggregation of commands written while Lettuce auto-flush is disabled. Per-connection
 * and per-command state is stored in {@link VirtualField}s, and the aggregate span lifecycle is
 * driven through the {@link LettuceBatching} seam in the library module.
 */
public class LettuceBatchSupport implements LettuceBatching.BatchHook {

  // Per-connection collection of commands written while auto-flush is disabled. Lettuce documents
  // manual flushing as reliable only for single-threaded use or with external synchronization, so
  // this state follows the same assumption.
  private static final VirtualField<RedisChannelWriter, BatchState> writerBatchState =
      VirtualField.find(RedisChannelWriter.class, BatchState.class);
  // Links each command in a flushed batch back to its aggregate scope. Stored directly on the
  // command via a virtual field, so the command hot path needs no shared map or lock.
  private static final VirtualField<RedisCommand<?, ?, ?>, BatchScope> commandBatchScope =
      VirtualField.find(RedisCommand.class, BatchScope.class);
  private static final ThreadLocal<BatchScope> currentBatchScope = new ThreadLocal<>();

  static {
    LettuceBatching.setHook(new LettuceBatchSupport());
  }

  private LettuceBatchSupport() {}

  public static void setAutoFlushCommands(RedisChannelWriter writer, boolean autoFlush) {
    writerBatchState.set(writer, autoFlush ? null : new BatchState());
  }

  public static void capture(RedisChannelWriter writer, RedisCommand<?, ?, ?> command) {
    BatchState batchState = writerBatchState.get(writer);
    if (batchState != null) {
      batchState.capture(command);
    }
  }

  @Nullable
  public static BatchScope startBatch(RedisChannelWriter writer) {
    BatchState batchState = writerBatchState.get(writer);
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
    BatchScope batchScope = commandBatchScope.get(command);
    if (batchScope == null) {
      batchScope = commandBatchScope.get(CommandWrapper.unwrap(command));
    }
    if (batchScope != null) {
      currentBatchScope.set(batchScope);
    }
  }

  public static void endCommand() {
    currentBatchScope.remove();
  }

  @Override
  @Nullable
  public BatchScope captureCurrent(BatchSpan span) {
    BatchScope batchScope = currentBatchScope.get();
    if (batchScope == null) {
      return null;
    }
    batchScope.capture(span);
    return batchScope;
  }

  static class BatchState {
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

  public static class BatchScope implements LettuceBatching.BatchScope {
    private final List<RedisCommand<?, ?, ?>> commands;
    private final AtomicInteger remaining;
    @Nullable private BatchSpan aggregateSpan;
    @Nullable private Throwable error;
    @Nullable private String errorMessage;

    private BatchScope(List<RedisCommand<?, ?, ?>> commands) {
      this.commands = commands;
      this.remaining = new AtomicInteger(commands.size());
      for (RedisCommand<?, ?, ?> command : commands) {
        commandBatchScope.set(command, this);
      }
    }

    private synchronized void capture(BatchSpan span) {
      if (aggregateSpan == null) {
        aggregateSpan = span.createAggregateSpan(commands);
      }
    }

    @Override
    public synchronized void finishOne(BatchSpan span) {
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
      BatchSpan span = aggregateSpan;
      if (span == null) {
        return;
      }
      aggregateSpan = null;
      for (RedisCommand<?, ?, ?> command : commands) {
        commandBatchScope.set(command, null);
      }
      if (throwable != null) {
        error = throwable;
      }
      span.finishWithResponse(errorMessage, error);
    }
  }
}
