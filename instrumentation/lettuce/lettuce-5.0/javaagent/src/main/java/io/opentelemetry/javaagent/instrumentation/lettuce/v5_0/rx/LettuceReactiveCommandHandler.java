/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceCommandPeer;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceConnectionState;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public abstract class LettuceReactiveCommandHandler {

  private static final Logger logger =
      Logger.getLogger(LettuceReactiveCommandHandler.class.getName());

  private final StatefulConnection<?, ?> connection;
  private final AtomicBoolean spanEnded = new AtomicBoolean();
  @Nullable private RedisCommand<?, ?, ?> command;
  @Nullable private Context context;
  private boolean expectsResponse;

  protected LettuceReactiveCommandHandler(StatefulConnection<?, ?> connection) {
    this.connection = connection;
  }

  public final void onCommand(RedisCommand<?, ?, ?> command) {
    this.command = command;
    expectsResponse = expectsResponse(command);
    LettuceCommandPeer.initializeForSubscription(command);
    LettuceConnectionState.copy(connection, command);
    context = instrumenter().start(Context.current(), command);
    if (!expectsResponse) {
      Context completedContext = context;
      this.context = null;
      this.command = null;
      instrumenter().end(completedContext, command, null, null);
    }
  }

  protected final boolean commandExpectsResponse() {
    return expectsResponse;
  }

  protected final void finishSpan(boolean isCommandCancelled, @Nullable Throwable throwable) {
    // A terminal signal can race cancellation from the subscribing thread.
    if (!expectsResponse || !spanEnded.compareAndSet(false, true)) {
      return;
    }
    Context completedContext = context;
    RedisCommand<?, ?, ?> completedCommand = command;
    context = null;
    command = null;
    if (completedContext != null && completedCommand != null) {
      onSpanEnding(completedContext, isCommandCancelled);
      instrumenter().end(completedContext, completedCommand, null, throwable);
    } else {
      logger.fine("Failed to end reactive Lettuce span because it probably wasn't started.");
    }
  }

  protected void onSpanEnding(Context context, boolean isCommandCancelled) {}

  public void onCancel() {
    finishSpan(/* isCommandCancelled= */ true, null);
  }
}
