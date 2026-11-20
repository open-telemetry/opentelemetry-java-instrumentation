/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Mono;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractSubscriptionTest {

  private final InstrumentationExtension testing;

  protected AbstractSubscriptionTest(InstrumentationExtension testing) {
    this.testing = testing;
  }

  @Test
  void subscription() {
    Mono<Connection> connection = Mono.create(sink -> sink.success(new Connection()));
    testing.runWithSpan(
        "parent", () -> connection.delayElement(Duration.ofMillis(1)).subscribe(Connection::query));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("Connection.query").hasParent(trace.getSpan(0))));
  }

  private class Connection {
    void query() {
      testing.runWithSpan("Connection.query", () -> {});
    }
  }
}
