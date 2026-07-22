/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.jms.ConnectionFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.core.JmsTemplate;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpringJmsListenerTest extends AbstractSpringJmsListenerTest {

  @Override
  void assertSpringJmsListener() {
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasNoParent(),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "publish spring-jms-listener"
                              : "spring-jms-listener publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "jms"),
                          equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                          oldOperation("publish"),
                          operationName("publish"),
                          operationType("publish"),
                          satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive spring-jms-listener"
                                : "spring-jms-listener receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process spring-jms-listener"
                                : "spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  @SuppressWarnings("unchecked")
  void shouldCaptureHeaders(Class<?> configClass) throws Exception {
    // given
    SpringApplication app = new SpringApplication(configClass);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    String message = "hello there";

    // when
    testing.runWithSpan(
        "parent",
        () ->
            jmsTemplate.convertAndSend(
                "spring-jms-listener",
                message,
                jmsMessage -> {
                  jmsMessage.setStringProperty("Test_Message_Header", "test");
                  jmsMessage.setStringProperty("Uncaptured_Header", "password");
                  jmsMessage.setIntProperty("Test_Message_Int_Header", 1234);
                  return jmsMessage;
                }));

    // then
    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, SECONDS)).isEqualTo(message);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "publish spring-jms-listener"
                                : "spring-jms-listener publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("publish"),
                            operationName("publish"),
                            operationType("publish"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234")))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive spring-jms-listener"
                                : "spring-jms-listener receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process spring-jms-listener"
                                : "spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  private static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationType(String operation) {
    String operationType = operation.equals("publish") ? "send" : operation;
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operationType : null);
  }
}
