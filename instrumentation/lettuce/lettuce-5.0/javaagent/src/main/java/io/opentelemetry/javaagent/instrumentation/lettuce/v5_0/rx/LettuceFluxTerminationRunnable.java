/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.instrumenter;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public class LettuceFluxTerminationRunnable implements Consumer<Signal<?>>, Runnable {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .getBoolean("experimental_span_attributes/development", false);
  private static final Logger logger =
      Logger.getLogger(LettuceFluxTerminationRunnable.class.getName());

  private final FluxOnSubscribeConsumer onSubscribeConsumer;
  @Nullable private RedisCommand<?, ?, ?> command;
  @Nullable private Context context;
  private int numResults;

  public static <T> Flux<T> monitor(
      Flux<T> publisher, StatefulConnection<?, ?> connection, boolean expectsResponse) {
    return Flux.defer(
        () -> {
          LettuceFluxTerminationRunnable handler =
              new LettuceFluxTerminationRunnable(connection, expectsResponse);
          Flux<T> monitoredPublisher = publisher.doOnSubscribe(handler.onSubscribeConsumer);
          if (expectsResponse) {
            monitoredPublisher = monitoredPublisher.doOnEach(handler).doOnCancel(handler);
          }
          return monitoredPublisher;
        });
  }

  private LettuceFluxTerminationRunnable(
      StatefulConnection<?, ?> connection, boolean expectsResponse) {
    onSubscribeConsumer = new FluxOnSubscribeConsumer(this, connection, expectsResponse);
  }

  private void finishSpan(boolean isCommandCancelled, Throwable throwable) {
    if (context != null && command != null) {
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        Span span = Span.fromContext(context);
        span.setAttribute("lettuce.command.results.count", numResults);
        if (isCommandCancelled) {
          span.setAttribute("lettuce.command.cancelled", true);
        }
      }
      instrumenter().end(context, command, null, throwable);
    } else {
      logger.fine(
          "Failed to end this.context, LettuceFluxTerminationRunnable cannot find this.context "
              + "because it probably wasn't started.");
    }
  }

  @Override
  public void accept(Signal<?> signal) {
    if (signal.getType() == SignalType.ON_COMPLETE || signal.getType() == SignalType.ON_ERROR) {
      finishSpan(/* isCommandCancelled= */ false, signal.getThrowable());
    } else if (signal.getType() == SignalType.ON_NEXT) {
      ++numResults;
    }
  }

  @Override
  public void run() {
    finishSpan(/* isCommandCancelled= */ true, null);
  }

  private static class FluxOnSubscribeConsumer implements Consumer<Subscription> {

    private final LettuceFluxTerminationRunnable owner;
    private final StatefulConnection<?, ?> connection;
    private final boolean expectsResponse;

    private FluxOnSubscribeConsumer(
        LettuceFluxTerminationRunnable owner,
        StatefulConnection<?, ?> connection,
        boolean expectsResponse) {
      this.owner = owner;
      this.connection = connection;
      this.expectsResponse = expectsResponse;
    }

    @Override
    public void accept(Subscription subscription) {
      RedisCommand<?, ?, ?> command = LettuceReactiveCommandContext.current();
      if (command == null) {
        logger.fine("Failed to correlate a Lettuce reactive subscription with its command.");
        return;
      }
      owner.command = command;
      LettuceSingletons.initializeCommandPeerForSubscription(command);
      LettuceSingletons.attachAddress(command, connection);
      owner.context = instrumenter().start(Context.current(), command);
      if (!expectsResponse) {
        instrumenter().end(owner.context, command, null, null);
      }
    }
  }
}
