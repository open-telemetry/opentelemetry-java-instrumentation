/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.ConnectionFactory;
import listener.AbstractConfig;
import listener.AnnotatedListenerConfig;
import listener.ManualListenerConfig;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jms.core.JmsTemplate;

public class SpringListenerTest {

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
                              val -> val.isInstanceOf(String.class))));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SpringListenerJms2 receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "SpringListenerJms2"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SpringListenerJms2 process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
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
