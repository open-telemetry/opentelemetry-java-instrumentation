/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;

import io.opentelemetry.instrumentation.spring.jms.v2_0.AbstractJmsTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.ConnectionFactory;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jms.core.JmsTemplate;

class SpringListenerTest extends AbstractJmsTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  void receivingMessageInSpringListenerGeneratesSpans(Class<AbstractConfig> config) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(config);
    ConnectionFactory factory = context.getBean(ConnectionFactory.class);
    JmsTemplate template = new JmsTemplate(factory);

    template.convertAndSend("SpringListenerJms2", "a message");

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(PRODUCER, CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertProducerSpan(span, "SpringListenerJms2", false));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        null,
                        "SpringListenerJms2",
                        "receive",
                        false,
                        null),
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        trace.getSpan(0),
                        "SpringListenerJms2",
                        "process",
                        false,
                        null)));
    context.close();
  }
}
