/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractSpringCloudStreamProducerTest
    extends AbstractRabbitProducerConsumerTest {

  protected final InstrumentationExtension testing;

  private static final boolean HAS_PRODUCER_SPAN =
      Boolean.getBoolean("otel.instrumentation.spring-integration.producer.enabled");

  private final Class<?> additionalContextClass;

  public AbstractSpringCloudStreamProducerTest(
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
  public void hasProducerSpan() {
    assumeTrue(HAS_PRODUCER_SPAN);

    producerContext.getBean("producer", Runnable.class).run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("producer");
                  span.hasKind(SpanKind.INTERNAL);
                },
                span -> {
                  span.hasName("testProducer.output publish");
                  span.hasKind(SpanKind.PRODUCER);
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
