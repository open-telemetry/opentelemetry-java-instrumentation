/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import javax.jms.ConnectionFactory;
import listener.AnnotatedListenerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jms.core.JmsTemplate;

public class SpringListenerSuppressReceiveSpansTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void receivingMessageInSpringListenerGeneratesSpans() {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AnnotatedListenerConfig.class);
    ConnectionFactory factory = context.getBean(ConnectionFactory.class);
    JmsTemplate template = new JmsTemplate(factory);

    template.convertAndSend("SpringListenerJms2", "a message");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SpringListenerJms2 publish")
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "SpringListenerJms2"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SpringListenerJms2 process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "SpringListenerJms2"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                val -> val.isInstanceOf(String.class)))));
    context.close();
  }
}
