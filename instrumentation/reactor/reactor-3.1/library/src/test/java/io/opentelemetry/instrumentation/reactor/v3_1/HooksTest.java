/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class HooksTest {

  @Test
  void canResetOurHooks() {
    ContextPropagationOperator operator = ContextPropagationOperator.create();
    AtomicReference<CoreSubscriber<? super Integer>> subscriber = new AtomicReference<>();

    new CapturingMono(subscriber).map(i -> i + 1).subscribe();
    assertThat(subscriber.get()).extracting("actual").isNotInstanceOf(TracingSubscriber.class);

    operator.registerOnEachOperator();
    new CapturingMono(subscriber).map(i -> i + 1).subscribe();
    assertThat(subscriber.get()).extracting("actual").isInstanceOf(TracingSubscriber.class);

    operator.resetOnEachOperator();
    new CapturingMono(subscriber).map(i -> i + 1).subscribe();
    assertThat(subscriber.get()).extracting("actual").isNotInstanceOf(TracingSubscriber.class);
  }

  private static class CapturingMono extends Mono<Integer> {
    final AtomicReference<CoreSubscriber<? super Integer>> subscriber;

    CapturingMono(AtomicReference<CoreSubscriber<? super Integer>> subscriber) {
      this.subscriber = subscriber;
    }

    @Override
    public void subscribe(CoreSubscriber<? super Integer> actual) {
      subscriber.set(actual);
    }
  }

  @Test
  void testInvalidBlockUsage() throws InterruptedException {
    ContextPropagationOperator operator = ContextPropagationOperator.create();
    operator.registerOnEachOperator();

    Callable<String> callable =
        () -> {
          Mono.just("test1").block();
          return "call1";
        };

    Disposable disposable =
        Mono.defer(
                () ->
                    Mono.fromCallable(callable).publishOn(Schedulers.single()).flatMap(Mono::just))
            .subscribeOn(Schedulers.single())
            .subscribe();

    TimeUnit.MILLISECONDS.sleep(100);

    disposable.dispose();
    operator.resetOnEachOperator();
  }
}
