/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class SpringRabbitMqTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> rabbitMqContainer;
  private static ConfigurableApplicationContext applicationContext;
  private static ConnectionFactory connectionFactory;

  private static String ip;

  @BeforeAll
  static void setUp() throws UnknownHostException {
    rabbitMqContainer =
        new GenericContainer<>("rabbitmq:4.2")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    cleanup.deferAfterAll(rabbitMqContainer::stop);
    rabbitMqContainer.start();

    SpringApplication app = new SpringApplication(ConsumerConfig.class);
    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");
    props.put("spring.rabbitmq.host", rabbitMqContainer.getHost());
    props.put("spring.rabbitmq.port", rabbitMqContainer.getMappedPort(5672));
    app.setDefaultProperties(props);

    applicationContext = app.run();
    cleanup.deferAfterAll(applicationContext);

    connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(rabbitMqContainer.getHost());
    connectionFactory.setPort(rabbitMqContainer.getMappedPort(5672));
    ip = InetAddress.getByName(rabbitMqContainer.getHost()).getHostAddress();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> getAssertions(
      String destination,
      String operation,
      String peerAddress,
      boolean routingKey,
      boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                equalTo(MESSAGING_DESTINATION_NAME, destination),
                satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
                equalTo(MESSAGING_OPERATION, emitStableMessagingSemconv() ? null : operation),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null),
                equalTo(
                    MESSAGING_OPERATION_TYPE,
                    emitStableMessagingSemconv()
                        ? "publish".equals(operation) ? "send" : operation
                        : null)));
    if (peerAddress != null) {
      assertions.add(equalTo(NETWORK_TYPE, "ipv4"));
      assertions.add(equalTo(NETWORK_PEER_ADDRESS, peerAddress));
      assertions.add(satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative));
      assertions.add(equalTo(SERVER_ADDRESS, emitStableMessagingSemconv() ? peerAddress : null));
      assertions.add(
          satisfies(
              SERVER_PORT,
              val -> {
                if (emitStableMessagingSemconv()) {
                  val.isNotNegative();
                } else {
                  val.isNull();
                }
              }));
    }
    if (routingKey) {
      assertions.add(
          satisfies(MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, AbstractStringAssert::isNotBlank));
    }
    assertions.add(
        satisfies(
            longKey("messaging.rabbitmq.message.delivery_tag"),
            val -> {
              if (emitStableMessagingSemconv() && "process".equals(operation)) {
                val.isNotNegative();
              } else {
                val.isNull();
              }
            }));
    if (testHeaders) {
      assertions.add(equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    return assertions;
  }

  private static List<AttributeAssertion> getAnonymousQueueAssertions(
      String queueName, String operation) {
    List<AttributeAssertion> assertions =
        getAssertions(
            emitStableMessagingSemconv() ? queueName : "<default>", operation, ip, true, false);
    assertions.add(
        equalTo(
            MESSAGING_DESTINATION_ANONYMOUS,
            (emitStableMessagingSemconv() || "process".equals(operation)) ? true : null));
    return assertions;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testContextPropagation(boolean testHeaders) throws Exception {
    Connection connection = connectionFactory.newConnection();
    cleanup.deferCleanup(connection);
    Channel channel = connection.createChannel();
    cleanup.deferCleanup(channel);

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
                      message.getMessageProperties().setHeader("Test-Message-Header", "test");
                      message.getMessageProperties().setHeader("Uncaptured-Header", "password");
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
          SpanData producerSpan = trace.getSpan(1);
          SpanData firstProcessSpan = trace.getSpan(2);
          SpanData secondProcessSpan = trace.getSpan(3);
          SpanData rabbitProcessSpan =
              "io.opentelemetry.rabbitmq-2.7"
                      .equals(firstProcessSpan.getInstrumentationScopeInfo().getName())
                  ? firstProcessSpan
                  : secondProcessSpan;
          SpanData springProcessSpan =
              rabbitProcessSpan == firstProcessSpan ? secondProcessSpan : firstProcessSpan;

          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("parent"),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "publish " + ConsumerConfig.TEST_QUEUE
                              : "<default> publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          getAssertions(
                              emitStableMessagingSemconv()
                                  ? ConsumerConfig.TEST_QUEUE
                                  : "<default>",
                              "publish",
                              ip,
                              true,
                              testHeaders)),
              // spring-cloud-stream-binder-rabbit listener puts all messages into a
              // BlockingQueue immediately after receiving
              // that's why the rabbitmq CONSUMER span will never have any child span (and
              // propagate context, actually)
              span -> {
                span.hasName(
                        emitStableMessagingSemconv()
                            ? "process " + ConsumerConfig.TEST_QUEUE
                            : "testQueue process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(producerSpan)
                    .hasAttributesSatisfyingExactly(
                        getAssertions(
                            emitStableMessagingSemconv() ? ConsumerConfig.TEST_QUEUE : "<default>",
                            "process",
                            ip,
                            true,
                            testHeaders));
                verifyLink(span, null);
              },
              // created by spring-rabbit instrumentation
              span -> {
                span.hasName(
                        emitStableMessagingSemconv()
                            ? "process " + ConsumerConfig.TEST_QUEUE
                            : "testQueue process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(producerSpan)
                    .hasAttributesSatisfyingExactly(
                        getAssertions(
                            ConsumerConfig.TEST_QUEUE, "process", null, true, testHeaders));
                verifyLink(span, null);
              },
              span -> span.hasName("consumer").hasParent(springProcessSpan));
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("basic.ack")
                      .hasKind(SpanKind.CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(NETWORK_TYPE, "ipv4"),
                          equalTo(NETWORK_PEER_ADDRESS, ip),
                          satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative),
                          equalTo(MESSAGING_SYSTEM, "rabbitmq")));
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"anonymousQueue", "legacyAnonymousQueue"})
  void testAnonymousQueueSpanName(String queueBeanName) throws Exception {
    Connection connection = connectionFactory.newConnection();
    cleanup.deferCleanup(connection);
    Channel channel = connection.createChannel();
    cleanup.deferCleanup(channel);

    String anonymousQueueName = applicationContext.getBean(queueBeanName, Queue.class).getName();
    applicationContext.getBean(AmqpTemplate.class).convertAndSend(anonymousQueueName, "test");
    applicationContext.getBean(AmqpTemplate.class).receive(anonymousQueueName, 5000);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "publish" : "<default> publish")
                        .hasAttributesSatisfyingExactly(
                            getAnonymousQueueAssertions(anonymousQueueName, "publish")),
                // Verify that a constant span name is used instead of the randomly generated
                // anonymous queue name
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process"
                                : queueBeanName.equals("anonymousQueue")
                                    ? "<generated> process"
                                    : anonymousQueueName + " process")
                        .hasAttributesSatisfyingExactly(
                            getAnonymousQueueAssertions(anonymousQueueName, "process"))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("basic.qos")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("basic.consume")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("basic.cancel")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("basic.ack")));
  }

  private static void verifyLink(SpanDataAssert span, SpanData linkSpan) {
    if (linkSpan == null) {
      span.hasTotalRecordedLinks(0);
    } else {
      span.hasLinks(LinkData.create(linkSpan.getSpanContext()));
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {

    static final String TEST_QUEUE = "testQueue";
    static final String LEGACY_ANONYMOUS_QUEUE = "123e4567-e89b-12d3-a456-426614174000";

    @Bean
    Queue testQueue() {
      return new Queue(TEST_QUEUE);
    }

    @Bean
    AnonymousQueue anonymousQueue() {
      return new AnonymousQueue();
    }

    @Bean
    Queue legacyAnonymousQueue() {
      return new Queue(LEGACY_ANONYMOUS_QUEUE);
    }

    @RabbitListener(queues = TEST_QUEUE)
    void consume(String ignored) {
      GlobalTraceUtil.runWithSpan("consumer", () -> {});
    }
  }
}
