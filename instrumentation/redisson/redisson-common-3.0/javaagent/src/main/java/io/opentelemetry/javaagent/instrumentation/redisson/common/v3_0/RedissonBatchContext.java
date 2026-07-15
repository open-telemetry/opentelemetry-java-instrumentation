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
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;

public final class RedissonBatchContext {
  private static final ContextKey<Boolean> KEY =
      ContextKey.named("opentelemetry-redisson-atomic-batch");
  private static final ContextKey<CommandCapture> CAPTURE_KEY =
      ContextKey.named("opentelemetry-redisson-batch-command-capture");
  private static final VirtualField<CompletionStage<?>, RedissonBatchMarker> FUTURE_MARKER_FIELD =
      VirtualField.find(CompletionStage.class, RedissonBatchMarker.class);
  private static final VirtualField<CommandData<?, ?>, RedissonBatchMarker> COMMAND_MARKER_FIELD =
      VirtualField.find(CommandData.class, RedissonBatchMarker.class);
  private static final VirtualField<RedisConnection, RedissonBatchMarker> CONNECTION_MARKER_FIELD =
      VirtualField.find(RedisConnection.class, RedissonBatchMarker.class);

  public static Context mark(Context context) {
    return context.with(KEY, true).with(CAPTURE_KEY, null);
  }

  public static boolean isActive(Context context) {
    return Boolean.TRUE.equals(context.get(KEY));
  }

  public static Context clearMarker(Context context) {
    if (!isActive(context) && context.get(CAPTURE_KEY) == null) {
      return context;
    }
    return context.with(KEY, false).with(CAPTURE_KEY, null);
  }

  @Nullable
  public static Scope startCapture() {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    return mark(Context.current()).makeCurrent();
  }

  @Nullable
  static Scope startCapture(
      RedissonBatchState state, RedisCommand<?> command, Codec codec, Object[] parameters) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    Context context =
        Context.current()
            .with(KEY, true)
            .with(CAPTURE_KEY, new CommandCapture(state, command, codec, parameters));
    return context.makeCurrent();
  }

  @Nullable
  static Scope startCandidateCapture(
      RedissonBatchState state, RedisCommand<?> command, Codec codec, Object[] parameters) {
    if (!emitStableDatabaseSemconv()) {
      return null;
    }
    Context context =
        Context.current()
            .with(KEY, false)
            .with(CAPTURE_KEY, new CommandCapture(state, command, codec, parameters));
    return context.makeCurrent();
  }

  public static void captureCommand(Object batchCommand, int index) {
    if (!emitStableDatabaseSemconv()) {
      return;
    }
    CommandCapture capture = Context.current().get(CAPTURE_KEY);
    if (capture != null) {
      capture.capture(batchCommand, index);
    }
  }

  public static void markCommand(Object command) {
    if (emitStableDatabaseSemconv()
        && command instanceof CommandData
        && isActive(Context.current())) {
      markCapturedCommand(command);
    }
  }

  static void markCapturedCommand(Object command) {
    if (command instanceof CommandData) {
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
    Object channel = connection.getChannel();
    RedissonBatchMarker connectionMarker = CONNECTION_MARKER_FIELD.get(connection);
    boolean connectionMarked = connectionMarker != null && connectionMarker.matches(channel);
    if (connectionMarker != null && !connectionMarked) {
      CONNECTION_MARKER_FIELD.set(connection, null);
    }
    if (request.isTransactionCompletion()) {
      CONNECTION_MARKER_FIELD.set(connection, null);
      return connectionMarked || request.isMarkedBatchCommand();
    }
    if (request.isMarkedBatchCommand()) {
      CONNECTION_MARKER_FIELD.set(connection, new RedissonBatchMarker(channel));
      return true;
    }
    if (connectionMarked) {
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

  private static class CommandCapture {
    private final RedissonBatchState state;
    private final RedisCommand<?> command;
    private final Codec codec;
    private final Object[] parameters;

    private CommandCapture(
        RedissonBatchState state, RedisCommand<?> command, Codec codec, Object[] parameters) {
      this.state = state;
      this.command = command;
      this.codec = codec;
      this.parameters = parameters;
    }

    private void capture(Object batchCommand, int index) {
      state.add(batchCommand, index, command, codec, parameters);
    }
  }

  private RedissonBatchContext() {}
}
