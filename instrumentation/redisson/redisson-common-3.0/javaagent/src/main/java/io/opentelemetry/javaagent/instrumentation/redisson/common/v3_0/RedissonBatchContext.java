/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

public final class RedissonBatchContext {
  private static final ContextKey<Boolean> KEY =
      ContextKey.named("opentelemetry-redisson-atomic-batch");
  private static final VirtualField<CompletionStage<?>, RedissonBatchMarker> FUTURE_MARKER_FIELD =
      VirtualField.find(CompletionStage.class, RedissonBatchMarker.class);
  private static final VirtualField<CommandData<?, ?>, RedissonBatchMarker> COMMAND_MARKER_FIELD =
      VirtualField.find(CommandData.class, RedissonBatchMarker.class);
  private static final VirtualField<RedisConnection, RedissonBatchMarker> CONNECTION_MARKER_FIELD =
      VirtualField.find(RedisConnection.class, RedissonBatchMarker.class);

  public static Context mark(Context context) {
    return context.with(KEY, true);
  }

  public static boolean isActive(Context context) {
    return Boolean.TRUE.equals(context.get(KEY));
  }

  @Nullable
  public static Scope startCapture() {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    return mark(Context.current()).makeCurrent();
  }

  public static void markCommand(Object command) {
    if (emitStableDatabaseSemconv()
        && command instanceof CommandData
        && isActive(Context.current())) {
      COMMAND_MARKER_FIELD.set((CommandData<?, ?>) command, new RedissonBatchMarker());
    }
  }

  public static boolean isMarkedCommand(Object command) {
    if (command instanceof CommandData) {
      return COMMAND_MARKER_FIELD.get((CommandData<?, ?>) command) != null;
    }
    if (command instanceof CommandsData) {
      for (CommandData<?, ?> singleCommand : ((CommandsData) command).getCommands()) {
        if (COMMAND_MARKER_FIELD.get(singleCommand) != null) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean shouldSuppress(RedisConnection connection, RedissonRequest request) {
    if (!emitStableDatabaseSemconv()) {
      return false;
    }
    if (request.isTransactionCompletion()) {
      CONNECTION_MARKER_FIELD.set(connection, null);
      return request.isMarkedBatchCommand();
    }
    if (request.isMarkedBatchCommand()) {
      CONNECTION_MARKER_FIELD.set(connection, new RedissonBatchMarker());
      return true;
    }
    if (CONNECTION_MARKER_FIELD.get(connection) != null) {
      return true;
    }
    return false;
  }

  public static void markFuture(Object future) {
    if (emitStableDatabaseSemconv() && future instanceof CompletionStage) {
      FUTURE_MARKER_FIELD.set((CompletionStage<?>) future, new RedissonBatchMarker());
    }
  }

  public static boolean isMarkedFuture(Object future) {
    return future instanceof CompletionStage
        && FUTURE_MARKER_FIELD.get((CompletionStage<?>) future) != null;
  }

  private RedissonBatchContext() {}
}
