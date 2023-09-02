/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.SemanticAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
public class ContextPropagationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static GenericContainer<?> rabbitMqContainer;
  private static ConfigurableApplicationContext applicationContext;
  private static ConnectionFactory connectionFactory;

  @BeforeAll
  static void setUp() {
    rabbitMqContainer =
        new GenericContainer<>("rabbitmq:latest")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    rabbitMqContainer.start();

    SpringApplication app = new SpringApplication(ConsumerConfig.class);
    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");
    props.put("spring.rabbitmq.host", rabbitMqContainer.getHost());
    props.put("spring.rabbitmq.port", rabbitMqContainer.getMappedPort(5672));
    app.setDefaultProperties(props);

    applicationContext = app.run();

    connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(rabbitMqContainer.getHost());
    connectionFactory.setPort(rabbitMqContainer.getMappedPort(5672));
  }

  @AfterAll
  static void teardown() {
    if (rabbitMqContainer != null) {
      rabbitMqContainer.stop();
    }
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  private static List<AttributeAssertion> getAssertions(
      String destination,
      String operation,
      String sockAddr,
      boolean routingKey,
      boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq"),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destination),
                satisfies(
                    SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                    AbstractLongAssert::isNotNegative)));
    if (operation != null) {
      assertions.add(equalTo(SemanticAttributes.MESSAGING_OPERATION, operation));
    }
    if (sockAddr != null) {
      assertions.add(equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, sockAddr));
      assertions.add(
          satisfies(SemanticAttributes.NET_SOCK_PEER_PORT, AbstractLongAssert::isNotNegative));
    }
    if (routingKey) {
      assertions.add(
          satisfies(
              SemanticAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
              AbstractStringAssert::isNotBlank));
    }
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void test(boolean testHeaders) throws Exception {
    try (Connection connection = connectionFactory.newConnection()) {
      try (Channel ignored = connection.createChannel()) {
        testing.runWithSpan(
            "parent",
            () -> {
              if (testHeaders) {
                applicationContext
                    .getBean(AmqpTemplate.class)
                    .convertAndSend(
                        ConsumerConfig.TEST_QUEUE,
                        "test",
                        message -> {
                          message.getMessageProperties().setHeader("test-message-header", "test");
                          return message;
                        });
              } else {
                applicationContext
                    .getBean(AmqpTemplate.class)
                    .convertAndSend(ConsumerConfig.TEST_QUEUE, "test");
              }
            });
        testing.waitAndAssertTraces(
            trace -> {
              trace
                  .hasSize(5)
                  .hasSpansSatisfyingExactly(
                      span -> span.hasName("parent"),
                      span ->
                          span.hasName("<default> publish")
                              .hasKind(SpanKind.PRODUCER)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  getAssertions("<default>", null, "127.0.0.1", true, testHeaders)),
                      // spring-cloud-stream-binder-rabbit listener puts all messages into a
                      // BlockingQueue immediately after receiving
                      // that's why the rabbitmq CONSUMER span will never have any child span (and
                      // propagate context, actually)
                      span ->
                          span.hasName("testQueue process")
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(1))
                              .hasAttributesSatisfyingExactly(
                                  getAssertions("<default>", "process", null, true, testHeaders)),
                      // created by spring-rabbit instrumentation
                      span ->
                          span.hasName("testQueue process")
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(1))
                              .hasAttributesSatisfyingExactly(
                                  getAssertions("testQueue", "process", null, false, testHeaders)),
                      span -> span.hasName("consumer").hasParent(trace.getSpan(3)));
            },
            trace -> {
              trace
                  .hasSize(1)
                  .hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("basic.ack")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                                  satisfies(
                                      SemanticAttributes.NET_SOCK_PEER_PORT,
                                      AbstractLongAssert::isNotNegative),
                                  equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")));
            });
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {

    static final String TEST_QUEUE = "testQueue";

    @Bean
    Queue testQueue() {
      return new Queue(TEST_QUEUE);
    }

    @RabbitListener(queues = TEST_QUEUE)
    void consume(String ignored) {
      GlobalTraceUtil.runWithSpan("consumer", () -> {});
    }
  }
}
