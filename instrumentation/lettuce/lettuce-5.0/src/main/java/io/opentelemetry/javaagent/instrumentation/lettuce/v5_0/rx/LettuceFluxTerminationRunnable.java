/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0.rx;

import static io.opentelemetry.instrumentation.auto.lettuce.v5_0.LettuceDatabaseClientTracer.TRACER;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.trace.Span;
import java.util.function.Consumer;
import org.reactivestreams.Subscription;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public class LettuceFluxTerminationRunnable implements Consumer<Signal<?>>, Runnable {

  private Span span = null;
  private int numResults = 0;
  private FluxOnSubscribeConsumer onSubscribeConsumer;

  public LettuceFluxTerminationRunnable(RedisCommand<?, ?, ?> command, boolean finishSpanOnClose) {
    onSubscribeConsumer = new FluxOnSubscribeConsumer(this, command, finishSpanOnClose);
  }

  public FluxOnSubscribeConsumer getOnSubscribeConsumer() {
    return onSubscribeConsumer;
  }

  private void finishSpan(boolean isCommandCancelled, Throwable throwable) {
    if (span != null) {
      span.setAttribute("db.command.results.count", numResults);
      if (isCommandCancelled) {
        span.setAttribute("db.command.cancelled", true);
      }
      if (throwable == null) {
        TRACER.end(span);
      } else {
        TRACER.endExceptionally(span, throwable);
      }
    } else {
      LoggerFactory.getLogger(Flux.class)
          .error(
              "Failed to finish this.span, LettuceFluxTerminationRunnable cannot find this.span "
                  + "because it probably wasn't started.");
    }
  }

  @Override
  public void accept(Signal signal) {
    if (SignalType.ON_COMPLETE.equals(signal.getType())
        || SignalType.ON_ERROR.equals(signal.getType())) {
      finishSpan(false, signal.getThrowable());
    } else if (SignalType.ON_NEXT.equals(signal.getType())) {
      ++numResults;
    }
  }

  @Override
  public void run() {
    finishSpan(true, null);
  }

  public static class FluxOnSubscribeConsumer implements Consumer<Subscription> {

    private final LettuceFluxTerminationRunnable owner;
    private final RedisCommand<?, ?, ?> command;
    private final boolean finishSpanOnClose;

    public FluxOnSubscribeConsumer(
        LettuceFluxTerminationRunnable owner,
        RedisCommand<?, ?, ?> command,
        boolean finishSpanOnClose) {
      this.owner = owner;
      this.command = command;
      this.finishSpanOnClose = finishSpanOnClose;
    }

    @Override
    public void accept(Subscription subscription) {
      owner.span = TRACER.startSpan(null, command);
      if (finishSpanOnClose) {
        TRACER.end(owner.span);
      }
    }
  }
}
