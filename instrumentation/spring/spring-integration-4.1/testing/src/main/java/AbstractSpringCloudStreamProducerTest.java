/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

public abstract class AbstractSpringCloudStreamProducerTest
    extends AbstractRabbitProducerConsumerTest {

  protected final InstrumentationExtension testing;

  private static final boolean HAS_PRODUCER_SPAN =
      Boolean.getBoolean("otel.instrumentation.spring-integration.producer.enabled");

  public AbstractSpringCloudStreamProducerTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    super(additionalContextClass);
    this.testing = testing;
  }

  @Test
  void has_producer_span() {
    assumeTrue(HAS_PRODUCER_SPAN);

    producerContext.getBean("producer", Runnable.class).run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer").hasKind(SpanKind.INTERNAL),
                span -> span.hasName("testProducer.output publish").hasKind(SpanKind.PRODUCER).hasParent(trace.getSpan(0)),
                span -> span.hasName("testConsumer.input process").hasKind(SpanKind.CONSUMER),
                span -> span.hasName("consumer").hasKind(SpanKind.INTERNAL)));
  }
}
