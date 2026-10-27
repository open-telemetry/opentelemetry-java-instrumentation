/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class LibraryTestingDependencyTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RepeatedTest(3)
  void runsWithoutAgentSupport() {
    ClassLoader loader = getClass().getClassLoader();
    assertThat(loader.getResource("io/opentelemetry/instrumentation/testing/AgentTestRunner.class"))
        .isNull();
    assertThat(loader.getResource("io/opentelemetry/javaagent/bootstrap/AgentInitializer.class"))
        .isNull();
    assertThat(ContextStorage.get().getClass().getName()).contains("StrictContextStorage");
    assertThat(Context.current()).isEqualTo(Context.root());
    assertThat(testing.spans()).isEmpty();

    testing.runWithSpan("library-consumer", () -> {});
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("library-consumer")));
  }
}
