/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractSpringCloudStreamRabbitTest
    extends AbstractRabbitProducerConsumerTest {

  protected final InstrumentationExtension testing;

  private final Class<?> additionalContextClass;

  public AbstractSpringCloudStreamRabbitTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    this.testing = testing;
    this.additionalContextClass = additionalContextClass;
  }

  @BeforeEach
  public void setupSpec() {
    startRabbit(additionalContextClass);
  }

  @AfterEach
  public void cleanupSpec() {
    stopRabbit();
  }

  @Test
  public void shouldPropagateContextThroughRabbitMQ() {
    producerContext.getBean("producer", Runnable.class).run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("producer");
                  span.hasKind(SpanKind.INTERNAL);
                },
                span -> {
                  span.hasName("testConsumer.input process");
                  span.hasKind(SpanKind.CONSUMER);
                },
                span -> {
                  span.hasName("consumer");
                  span.hasKind(SpanKind.INTERNAL);
                }));
  }
}
