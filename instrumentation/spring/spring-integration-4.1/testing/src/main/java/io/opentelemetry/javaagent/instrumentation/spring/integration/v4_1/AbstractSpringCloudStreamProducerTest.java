/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.messagingAttributes;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractSpringCloudStreamProducerTest {

  private static final boolean HAS_PRODUCER_SPAN =
      Boolean.getBoolean("otel.instrumentation.spring-integration.producer.enabled");

  @RegisterExtension private final RabbitExtension rabbit;

  private final InstrumentationExtension testing;

  AbstractSpringCloudStreamProducerTest(
      InstrumentationExtension testing, Class<?> additionalContextClass) {
    this.testing = testing;
    rabbit = new RabbitExtension(additionalContextClass);
  }

  @Test
  void hasProducerSpan() {
    assumeTrue(HAS_PRODUCER_SPAN);

    rabbit.getBean("producer", Runnable.class).run();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "publish testProducer.output"
                                : "testProducer.output publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("publish", "testProducer.output")),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process testConsumer.input"
                                : "testConsumer.input process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            messagingAttributes("process", "testConsumer.input")),
                span ->
                    span.hasName("consumer")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))));
  }
}
