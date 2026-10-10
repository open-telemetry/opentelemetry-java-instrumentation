/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import io.lettuce.core.api.StatefulConnection;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.function.Consumer;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public class LettuceFluxTerminationHandler extends LettuceReactiveCommandHandler
    implements Consumer<Signal<?>> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
          .getBoolean("experimental_span_attributes/development", false);

  private int numResults;

  public static <T> Flux<T> monitor(Flux<T> publisher, StatefulConnection<?, ?> connection) {
    return new Flux<T>() {
      @Override
      public void subscribe(CoreSubscriber<? super T> actual) {
        LettuceFluxTerminationHandler handler = new LettuceFluxTerminationHandler(connection);
        publisher
            .doOnEach(handler)
            .subscribe(new LettuceReactiveCommandSubscriber<>(actual, handler));
      }
    };
  }

  private LettuceFluxTerminationHandler(StatefulConnection<?, ?> connection) {
    super(connection);
  }

  @Override
  protected void onSpanEnding(Context context, boolean isCommandCancelled) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      Span span = Span.fromContext(context);
      span.setAttribute("lettuce.command.results.count", numResults);
      if (isCommandCancelled) {
        span.setAttribute("lettuce.command.cancelled", true);
      }
    }
  }

  @Override
  public void accept(Signal<?> signal) {
    if (!commandExpectsResponse()) {
      return;
    }
    if (signal.getType() == SignalType.ON_COMPLETE || signal.getType() == SignalType.ON_ERROR) {
      finishSpan(/* isCommandCancelled= */ false, signal.getThrowable());
    } else if (signal.getType() == SignalType.ON_NEXT) {
      ++numResults;
    }
  }
}
