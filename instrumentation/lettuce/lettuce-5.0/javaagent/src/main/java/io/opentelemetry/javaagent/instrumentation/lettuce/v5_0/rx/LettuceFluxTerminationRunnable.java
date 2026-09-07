/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
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
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public class LettuceFluxTerminationRunnable
    implements LettuceReactiveCommandHandler, Consumer<Signal<?>>, Runnable {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .getBoolean("experimental_span_attributes/development", false);
  private static final Logger logger =
      Logger.getLogger(LettuceFluxTerminationRunnable.class.getName());

  private final StatefulConnection<?, ?> connection;
  @Nullable private RedisCommand<?, ?, ?> command;
  @Nullable private Context context;
  private boolean expectsResponse;
  private int numResults;

  public static <T> Flux<T> monitor(Flux<T> publisher, StatefulConnection<?, ?> connection) {
    return new Flux<T>() {
      @Override
      public void subscribe(CoreSubscriber<? super T> actual) {
        LettuceFluxTerminationRunnable handler = new LettuceFluxTerminationRunnable(connection);
        publisher
            .doOnEach(handler)
            .subscribe(new LettuceReactiveCommandSubscriber<>(actual, handler));
      }
    };
  }

  private LettuceFluxTerminationRunnable(StatefulConnection<?, ?> connection) {
    this.connection = connection;
  }

  @Override
  public void onCommand(RedisCommand<?, ?, ?> command) {
    this.command = command;
    expectsResponse = expectsResponse(command);
    LettuceSingletons.initializeCommandPeerForSubscription(command);
    LettuceSingletons.attachAddress(command, connection);
    context = instrumenter().start(Context.current(), command);
    if (!expectsResponse) {
      instrumenter().end(context, command, null, null);
    }
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
    if (!expectsResponse) {
      return;
    }
    if (signal.getType() == SignalType.ON_COMPLETE || signal.getType() == SignalType.ON_ERROR) {
      finishSpan(/* isCommandCancelled= */ false, signal.getThrowable());
    } else if (signal.getType() == SignalType.ON_NEXT) {
      ++numResults;
    }
  }

  @Override
  public void run() {
    if (!expectsResponse) {
      return;
    }
    finishSpan(/* isCommandCancelled= */ true, null);
  }

  @Override
  public void onCancel() {
    run();
  }
}
