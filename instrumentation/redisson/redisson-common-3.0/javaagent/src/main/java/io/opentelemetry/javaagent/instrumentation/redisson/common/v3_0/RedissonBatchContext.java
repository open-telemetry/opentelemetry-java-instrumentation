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
  private static final VirtualField<CompletionStage<?>, RedissonBatchMarker> futureMarkerField =
      VirtualField.find(CompletionStage.class, RedissonBatchMarker.class);
  private static final VirtualField<CommandData<?, ?>, RedissonBatchMarker> commandMarkerField =
      VirtualField.find(CommandData.class, RedissonBatchMarker.class);
  private static final VirtualField<RedisConnection, RedissonBatchMarker> connectionMarkerField =
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
      commandMarkerField.set((CommandData<?, ?>) command, new RedissonBatchMarker());
    }
  }

  public static boolean isMarkedCommand(Object command) {
    if (command instanceof CommandData) {
      return commandMarkerField.get((CommandData<?, ?>) command) != null;
    }
    if (command instanceof CommandsData) {
      for (CommandData<?, ?> singleCommand : ((CommandsData) command).getCommands()) {
        if (commandMarkerField.get(singleCommand) != null) {
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
      connectionMarkerField.set(connection, null);
      return request.isMarkedBatchCommand();
    }
    if (request.isMarkedBatchCommand()) {
      connectionMarkerField.set(connection, new RedissonBatchMarker());
      return true;
    }
    if (connectionMarkerField.get(connection) != null) {
      return true;
    }
    return false;
  }

  public static void markFuture(Object future) {
    if (emitStableDatabaseSemconv() && future instanceof CompletionStage) {
      futureMarkerField.set((CompletionStage<?>) future, new RedissonBatchMarker());
    }
  }

  public static boolean isMarkedFuture(Object future) {
    return future instanceof CompletionStage
        && futureMarkerField.get((CompletionStage<?>) future) != null;
  }

  private RedissonBatchContext() {}
}
