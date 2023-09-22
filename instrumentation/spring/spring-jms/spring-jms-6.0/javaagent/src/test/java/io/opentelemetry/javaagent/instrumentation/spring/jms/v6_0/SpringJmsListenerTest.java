/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.jms.ConnectionFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.core.JmsTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

class SpringJmsListenerTest {

  static final Logger logger = LoggerFactory.getLogger(SpringJmsListenerTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static GenericContainer<?> broker;

  @BeforeAll
  static void setUp() {
    broker =
        new GenericContainer<>("quay.io/artemiscloud/activemq-artemis-broker:artemis.2.27.0")
            .withEnv("AMQ_USER", "test")
            .withEnv("AMQ_PASSWORD", "test")
            .withEnv("JAVA_TOOL_OPTIONS", "-Dbrokerconfig.maxDiskUsage=-1")
            .withExposedPorts(61616, 8161)
            .waitingFor(Wait.forLogMessage(".*Server is now live.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer(new Slf4jLogConsumer(logger));
    broker.start();
  }

  @AfterAll
  static void tearDown() {
    if (broker != null) {
      broker.close();
    }
  }

  @ArgumentsSource(ConfigClasses.class)
  @ParameterizedTest
  @SuppressWarnings("unchecked")
  void testSpringJmsListener(Class<?> configClass)
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    SpringApplication app = new SpringApplication(configClass);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    String message = "hello there";

    // when
    testing.runWithSpan("parent", () -> jmsTemplate.convertAndSend("spring-jms-listener", message));

    // then
    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, TimeUnit.SECONDS)).isEqualTo(message);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("spring-jms-listener publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank)),
                span ->
                    span.hasName("spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("spring-jms-listener receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank))));
  }

  @ArgumentsSource(ConfigClasses.class)
  @ParameterizedTest
  @SuppressWarnings("unchecked")
  void shouldCaptureHeaders(Class<?> configClass)
      throws ExecutionException, InterruptedException, TimeoutException {
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
                  jmsMessage.setStringProperty("test_message_header", "test");
                  jmsMessage.setIntProperty("test_message_int_header", 1234);
                  return jmsMessage;
                }));

    // then
    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, TimeUnit.SECONDS)).isEqualTo(message);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("spring-jms-listener publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234"))),
                span ->
                    span.hasName("spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("spring-jms-listener receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234")))));
  }

  private static Map<String, Object> defaultConfig() {
    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");
    props.put("test.broker-url", "tcp://localhost:" + broker.getMappedPort(61616));
    return props;
  }

  static final class ConfigClasses implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          arguments(AnnotatedListenerConfig.class), arguments(ManualListenerConfig.class));
    }
  }
}
