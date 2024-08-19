/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractSpringCloudStreamRabbitTest {

  @RegisterExtension RabbitExtension rabbit;

  protected final InstrumentationExtension testing;

  public AbstractSpringCloudStreamRabbitTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    this.testing = testing;
    rabbit = new RabbitExtension(testing, additionalContextClass);
  }

  @Test
  void should_propagate_context_through_rabbit_mq() {
    rabbit.getBean("producer", Runnable.class).run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("testConsumer.input process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("consumer")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
  }
}
