/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public class LettuceFluxTerminationRunnable implements Consumer<Signal<?>>, Runnable {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.lettuce.experimental-span-attributes", false);

  private Context context;
  private int numResults;
  private final FluxOnSubscribeConsumer onSubscribeConsumer;

  public LettuceFluxTerminationRunnable(RedisCommand<?, ?, ?> command, boolean expectsResponse) {
    onSubscribeConsumer = new FluxOnSubscribeConsumer(this, command, expectsResponse);
  }

  public FluxOnSubscribeConsumer getOnSubscribeConsumer() {
    return onSubscribeConsumer;
  }

  private void finishSpan(boolean isCommandCancelled, Throwable throwable) {
    if (context != null) {
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        Span span = Span.fromContext(context);
        span.setAttribute("lettuce.command.results.count", numResults);
        if (isCommandCancelled) {
          span.setAttribute("lettuce.command.cancelled", true);
        }
      }
      instrumenter().end(context, onSubscribeConsumer.command, null, throwable);
    } else {
      Logger.getLogger(Flux.class.getName())
          .severe(
              "Failed to end this.context, LettuceFluxTerminationRunnable cannot find this.context "
                  + "because it probably wasn't started.");
    }
  }

  @Override
  public void accept(Signal<?> signal) {
    if (SignalType.ON_COMPLETE.equals(signal.getType())
        || SignalType.ON_ERROR.equals(signal.getType())) {
      finishSpan(/* isCommandCancelled= */ false, signal.getThrowable());
    } else if (SignalType.ON_NEXT.equals(signal.getType())) {
      ++numResults;
    }
  }

  @Override
  public void run() {
    finishSpan(/* isCommandCancelled= */ true, null);
  }

  public static class FluxOnSubscribeConsumer implements Consumer<Subscription> {

    private final LettuceFluxTerminationRunnable owner;
    private final RedisCommand<?, ?, ?> command;
    private final boolean expectsResponse;

    public FluxOnSubscribeConsumer(
        LettuceFluxTerminationRunnable owner,
        RedisCommand<?, ?, ?> command,
        boolean expectsResponse) {
      this.owner = owner;
      this.command = command;
      this.expectsResponse = expectsResponse;
    }

    @Override
    public void accept(Subscription subscription) {
      owner.context = instrumenter().start(Context.current(), command);
      if (!expectsResponse) {
        instrumenter().end(owner.context, command, null, null);
      }
    }
  }
}
