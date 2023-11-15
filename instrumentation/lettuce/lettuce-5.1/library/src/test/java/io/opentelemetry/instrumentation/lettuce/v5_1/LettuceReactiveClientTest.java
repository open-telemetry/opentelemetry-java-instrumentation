/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.RedisClient;
import io.lettuce.core.resource.ClientResources;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class LettuceReactiveClientTest extends AbstractLettuceReactiveClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }

  private static ContextPropagationOperator tracingOperator = ContextPropagationOperator.create();

  @Override
  protected RedisClient createClient(String uri) {
    return RedisClient.create(
        ClientResources.builder()
            .tracing(
                LettuceTelemetry.create(getInstrumentationExtension().getOpenTelemetry())
                    .newTracing())
            .build(),
        uri);
  }

  @BeforeEach
  void setup() {
    tracingOperator.registerOnEachOperator();
  }

  @AfterEach
  void cleanup() {
    tracingOperator.resetOnEachOperator();
  }
}
