/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;

public abstract class AbstractSpringCloudStreamRabbitTest
    extends AbstractRabbitProducerConsumerTest {

  protected final InstrumentationExtension testing;

  public AbstractSpringCloudStreamRabbitTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    super(additionalContextClass);
    this.testing = testing;
  }

  @Test
  void should_propagate_context_through_rabbit_mq() {
    producerContext.getBean("producer", Runnable.class).run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer").hasKind(SpanKind.INTERNAL),
                span -> span.hasName("testConsumer.input process").hasKind(SpanKind.CONSUMER),
                span -> span.hasName("consumer").hasKind(SpanKind.INTERNAL)));
  }
}
