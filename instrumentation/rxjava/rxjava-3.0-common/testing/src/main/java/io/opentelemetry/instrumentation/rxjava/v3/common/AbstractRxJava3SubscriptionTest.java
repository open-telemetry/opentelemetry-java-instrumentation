/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

public abstract class AbstractRxJava3SubscriptionTest {

  protected abstract InstrumentationExtension testing();

  @Test
  public void subscriptionTest() throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    testing()
        .runWithSpan(
            "parent",
            () -> {
              Single<Connection> connectionSingle =
                  Single.create(emitter -> emitter.onSuccess(new Connection()));
              Disposable unused =
                  connectionSingle.subscribe(
                      connection -> {
                        connection.query();
                        countDownLatch.countDown();
                      });
            });
    countDownLatch.await();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("Connection.query")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  static class Connection {
    int query() {
      Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("Connection.query").startSpan();
      span.end();
      return new Random().nextInt();
    }
  }
}
