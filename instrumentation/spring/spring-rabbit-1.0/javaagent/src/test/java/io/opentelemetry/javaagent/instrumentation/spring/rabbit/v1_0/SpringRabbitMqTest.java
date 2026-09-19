/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0.SpringRabbitMetricsAssertions.assertProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0.SpringRabbitMetricsAssertions.assertRabbitProcessDuration;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    SpringApplication app = new SpringApplication(ConsumerConfig.class, DirectFactoryConfig.class);
    Map<String, Object> props = new HashMap<>();
    props.put("spring.jmx.enabled", false);
    props.put("spring.main.web-application-type", "none");
    props.put("spring.rabbitmq.host", rabbitMqContainer.getHost());
    props.put("spring.rabbitmq.port", rabbitMqContainer.getMappedPort(5672));
    props.put("spring.rabbitmq.listener.simple.default-requeue-rejected", false);
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
      boolean serverAttributes,
      boolean routingKey,
      boolean testHeaders,
      boolean messageBodySizePresent,
      Long expectedMessageBodySize,
      Long expectedBatchMessageCount) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                equalTo(MESSAGING_DESTINATION_NAME, destination),
                satisfies(
                    MESSAGING_MESSAGE_BODY_SIZE,
                    val -> {
                      if (emitOldMessagingSemconv() && messageBodySizePresent) {
                        if (expectedMessageBodySize == null) {
                          val.isNotNegative();
                        } else {
                          val.isEqualTo(expectedMessageBodySize);
                        }
                      } else {
                        val.isNull();
                      }
                    }),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null),
                equalTo(
                    MESSAGING_OPERATION_TYPE,
                    emitStableMessagingSemconv()
                        ? "publish".equals(operation) ? "send" : operation
                        : null),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, expectedBatchMessageCount)));
    if (peerAddress != null) {
      assertions.add(equalTo(NETWORK_TYPE, "ipv4"));
      assertions.add(equalTo(NETWORK_PEER_ADDRESS, peerAddress));
      assertions.add(satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative));
    }
    if (serverAttributes) {
      assertions.add(equalTo(SERVER_ADDRESS, emitStableMessagingSemconv() ? ip : null));
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
            MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG,
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
            emitStableMessagingSemconv() ? queueName : "<default>",
            operation,
            ip,
            true,
            true,
            false,
            true,
            null,
            null);
    assertions.add(
        equalTo(MESSAGING_DESTINATION_ANONYMOUS, emitStableMessagingSemconv() ? true : null));
    return assertions;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testContextPropagation(boolean testHeaders) throws Exception {
    sendAndAssertContextPropagation(ConsumerConfig.TEST_QUEUE, testHeaders);
  }

  @Test
  void testDirectContextPropagation() throws Exception {
    sendAndAssertContextPropagation(ConsumerConfig.DIRECT_QUEUE, false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testBatchListenerProcessTelemetryOwnership(boolean consumerBatchEnabled) throws Exception {
    assumeTrue(testLatestDeps());

    String queue = consumerBatchEnabled ? "consumerBatchQueue" : "batchListenerQueue";
    applicationContext.getBean(AmqpAdmin.class).declareQueue(new Queue(queue));
    CountDownLatch messageConsumed = new CountDownLatch(1);
    Class<?> listenerType = Class.forName("org.springframework.amqp.core.BatchMessageListener");
    MessageListener listener =
        (MessageListener)
            Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[] {listenerType},
                (proxy, method, args) -> {
                  if (method
                      .getName()
                      .equals(consumerBatchEnabled ? "onMessageBatch" : "onMessage")) {
                    messageConsumed.countDown();
                  }
                  return method.getReturnType() == boolean.class ? false : null;
                });
    SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
    container.setConnectionFactory(
        applicationContext.getBean(
            org.springframework.amqp.rabbit.connection.ConnectionFactory.class));
    container.setQueueNames(queue);
    container.setMessageListener(listener);
    if (consumerBatchEnabled) {
      SimpleMessageListenerContainer.class
          .getMethod("setConsumerBatchEnabled", boolean.class)
          .invoke(container, true);
    }
    cleanup.deferCleanup(container::stop);
    container.start();
    testing.waitForTraces(3);
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () -> applicationContext.getBean(AmqpTemplate.class).convertAndSend(queue, "test"));

    assertThat(messageConsumed.await(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace -> {
          SpanData producerSpan = trace.getSpan(1);
          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("parent"),
              span -> span.hasKind(SpanKind.PRODUCER).hasParent(trace.getSpan(0)),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv() ? "process " + queue : queue + " process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(producerSpan)
                      .satisfies(
                          spanData ->
                              assertThat(spanData.getInstrumentationScopeInfo().getName())
                                  .isEqualTo(
                                      consumerBatchEnabled
                                          ? "io.opentelemetry.rabbitmq-2.7"
                                          : "io.opentelemetry.spring-rabbit-1.0")));
        },
        trace -> trace.hasSpansSatisfyingExactly(SpringRabbitMqTest::verifyAckSpan));
    if (consumerBatchEnabled) {
      assertRabbitProcessDuration(testing, queue);
    } else {
      assertProcessMetrics(testing, queue, null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testProducerBatchProcessTelemetryOwnership(boolean directContainer) throws Exception {
    assumeTrue(testLatestDeps());

    String queue = directContainer ? "directProducerBatchQueue" : "simpleProducerBatchQueue";
    applicationContext.getBean(AmqpAdmin.class).declareQueue(new Queue(queue));
    CountDownLatch messageConsumed = new CountDownLatch(1);
    AtomicInteger receivedBatchSize = new AtomicInteger();
    Class<?> listenerType = Class.forName("org.springframework.amqp.core.BatchMessageListener");
    MessageListener listener =
        (MessageListener)
            Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[] {listenerType},
                (proxy, method, args) -> {
                  if (method.getName().equals("onMessageBatch")) {
                    receivedBatchSize.set(((List<?>) args[0]).size());
                    messageConsumed.countDown();
                  }
                  return method.getReturnType() == boolean.class ? false : null;
                });
    AbstractMessageListenerContainer container =
        directContainer
            ? new DirectMessageListenerContainer()
            : new SimpleMessageListenerContainer();
    container.setConnectionFactory(
        applicationContext.getBean(
            org.springframework.amqp.rabbit.connection.ConnectionFactory.class));
    container.setQueueNames(queue);
    container.setMessageListener(listener);
    cleanup.deferCleanup(container::stop);
    container.start();
    testing.waitForTraces(3);
    testing.clearData();

    Message batch = createProducerBatch(queue);
    testing.runWithSpan(
        "parent", () -> applicationContext.getBean(AmqpTemplate.class).send(queue, batch));

    assertThat(messageConsumed.await(10, SECONDS)).isTrue();
    assertThat(receivedBatchSize).hasValue(2);
    testing.waitAndAssertTraces(
        trace -> {
          SpanData producerSpan = trace.getSpan(1);
          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("parent"),
              span -> span.hasKind(SpanKind.PRODUCER).hasParent(trace.getSpan(0)),
              span -> {
                span.hasName(emitStableMessagingSemconv() ? "process " + queue : queue + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(producerSpan)
                    .satisfies(
                        spanData ->
                            assertThat(spanData.getInstrumentationScopeInfo().getName())
                                .isEqualTo("io.opentelemetry.spring-rabbit-1.0"))
                    .hasAttributesSatisfyingExactly(
                        getAssertions(
                            queue,
                            "process",
                            ip,
                            true,
                            emitStableMessagingSemconv(),
                            false,
                            false,
                            null,
                            2L));
                verifyLink(span, emitStableMessagingSemconv() ? producerSpan : null);
              });
        },
        trace -> trace.hasSpansSatisfyingExactly(SpringRabbitMqTest::verifyAckSpan));
    assertProcessMetrics(testing, queue, null, 2);
  }

  private static Message createProducerBatch(String queue) throws Exception {
    Class<?> strategyClass =
        Class.forName("org.springframework.amqp.rabbit.batch.SimpleBatchingStrategy");
    Object strategy =
        strategyClass.getConstructor(int.class, int.class, long.class).newInstance(2, 1024, 60_000);
    Method addToBatch =
        strategyClass.getMethod("addToBatch", String.class, String.class, Message.class);
    assertThat(
            addToBatch.invoke(
                strategy, "", queue, new Message("one".getBytes(UTF_8), new MessageProperties())))
        .isNull();
    Object messageBatch =
        addToBatch.invoke(
            strategy, "", queue, new Message("two".getBytes(UTF_8), new MessageProperties()));
    assertThat(messageBatch).isNotNull();
    return (Message) messageBatch.getClass().getMethod("getMessage").invoke(messageBatch);
  }

  private static void sendAndAssertContextPropagation(String queue, boolean testHeaders)
      throws Exception {
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
                    queue,
                    "test",
                    message -> {
                      message.getMessageProperties().setHeader("Test-Message-Header", "test");
                      message.getMessageProperties().setHeader("Uncaptured-Header", "password");
                      return message;
                    });
          } else {
            applicationContext.getBean(AmqpTemplate.class).convertAndSend(queue, "test");
          }
        });
    testing.waitAndAssertTraces(
        trace -> {
          SpanData producerSpan = trace.getSpan(1);
          SpanData springProcessSpan = trace.getSpan(2);

          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("parent"),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv() ? "publish " + queue : "<default> publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          getAssertions(
                              emitStableMessagingSemconv() ? queue : "<default>",
                              "publish",
                              ip,
                              true,
                              true,
                              testHeaders,
                              true,
                              null,
                              null)),
              // created by spring-rabbit instrumentation
              span -> {
                span.hasName(emitStableMessagingSemconv() ? "process " + queue : queue + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(producerSpan)
                    .hasAttributesSatisfyingExactly(
                        getAssertions(
                            queue,
                            "process",
                            ip,
                            true,
                            emitStableMessagingSemconv(),
                            testHeaders,
                            true,
                            4L,
                            null));
                verifyLink(span, emitStableMessagingSemconv() ? producerSpan : null);
              },
              span -> span.hasName("consumer").hasParent(springProcessSpan));
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(SpringRabbitMqTest::verifyAckSpan);
        });
    assertProcessMetrics(testing, queue, null);
  }

  @Test
  void testDefaultReceiveTelemetryMetricOwnership() throws InterruptedException {
    applicationContext
        .getBean(AmqpTemplate.class)
        .convertAndSend(ConsumerConfig.METRICS_QUEUE, "test");

    assertThat(ConsumerConfig.metricsMessageConsumed.await(10, SECONDS)).isTrue();
    assertProcessMetrics(testing, ConsumerConfig.METRICS_QUEUE, null);
  }

  @Test
  void testErrorMetrics() throws InterruptedException {
    applicationContext
        .getBean(AmqpTemplate.class)
        .convertAndSend(ConsumerConfig.ERROR_QUEUE, "test");

    assertThat(ConsumerConfig.errorMessageConsumed.await(10, SECONDS)).isTrue();
    assertProcessMetrics(
        testing,
        ConsumerConfig.ERROR_QUEUE,
        testLatestDeps()
            ? "org.springframework.amqp.rabbit.support.ListenerExecutionFailedException"
            : "org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException");
  }

  @Test
  void testDirectErrorMetrics() throws InterruptedException {
    applicationContext
        .getBean(AmqpTemplate.class)
        .convertAndSend(ConsumerConfig.DIRECT_ERROR_QUEUE, "test");

    assertThat(ConsumerConfig.directErrorMessageConsumed.await(10, SECONDS)).isTrue();
    assertProcessMetrics(
        testing,
        ConsumerConfig.DIRECT_ERROR_QUEUE,
        testLatestDeps()
            ? "org.springframework.amqp.rabbit.support.ListenerExecutionFailedException"
            : "org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException");
  }

  @ParameterizedTest
  @ValueSource(strings = {"anonymousQueue", "legacyAnonymousQueue", "anonymousGroupQueue"})
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
        trace -> trace.hasSpansSatisfyingExactly(SpringRabbitMqTest::verifyAckSpan));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void verifyAckSpan(SpanDataAssert span) {
    boolean stable = emitStableMessagingSemconv();
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(NETWORK_TYPE, "ipv4"),
                equalTo(NETWORK_PEER_ADDRESS, ip),
                satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative),
                equalTo(MESSAGING_SYSTEM, "rabbitmq")));
    if (stable) {
      assertions.add(equalTo(SERVER_ADDRESS, ip));
      assertions.add(satisfies(SERVER_PORT, AbstractLongAssert::isNotNegative));
      assertions.add(equalTo(MESSAGING_OPERATION_NAME, "ack"));
      assertions.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
      assertions.add(
          satisfies(MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG, AbstractLongAssert::isPositive));
      if (emitOldMessagingSemconv()) {
        assertions.add(equalTo(MESSAGING_OPERATION, "settle"));
      }
    }
    span.hasName(stable ? "ack" : "basic.ack")
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(assertions);
  }

  private static void verifyLink(SpanDataAssert span, SpanData linkSpan) {
    if (linkSpan == null) {
      span.hasTotalRecordedLinks(0);
    } else {
      span.hasLinks(LinkData.create(linkSpan.getSpanContext()));
    }
  }

  @Configuration
  static class DirectFactoryConfig {

    @Bean
    DirectRabbitListenerContainerFactory directFactory(
        org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
      DirectRabbitListenerContainerFactory factory = new DirectRabbitListenerContainerFactory();
      factory.setConnectionFactory(connectionFactory);
      factory.setDefaultRequeueRejected(false);
      return factory;
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {

    static final String TEST_QUEUE = "testQueue";
    static final String METRICS_QUEUE = "metricsQueue";
    static final String ERROR_QUEUE = "errorQueue";
    static final String DIRECT_QUEUE = "directQueue";
    static final String DIRECT_ERROR_QUEUE = "directErrorQueue";
    static final String LEGACY_ANONYMOUS_QUEUE = "123e4567-e89b-12d3-a456-426614174000";
    // the name that spring-cloud-stream's rabbit binder generates for a consumer without a group
    static final String ANONYMOUS_GROUP_QUEUE = "testDestination.anonymous.Q_bA0sGiTcyXMWXZMyOHwA";
    private static final CountDownLatch metricsMessageConsumed = new CountDownLatch(1);
    private static final CountDownLatch errorMessageConsumed = new CountDownLatch(1);
    private static final CountDownLatch directErrorMessageConsumed = new CountDownLatch(1);

    @Bean
    Queue testQueue() {
      return new Queue(TEST_QUEUE);
    }

    @Bean
    Queue metricsQueue() {
      return new Queue(METRICS_QUEUE);
    }

    @Bean
    Queue errorQueue() {
      return new Queue(ERROR_QUEUE);
    }

    @Bean
    Queue directQueue() {
      return new Queue(DIRECT_QUEUE);
    }

    @Bean
    Queue directErrorQueue() {
      return new Queue(DIRECT_ERROR_QUEUE);
    }

    @Bean
    AnonymousQueue anonymousQueue() {
      return new AnonymousQueue();
    }

    @Bean
    Queue legacyAnonymousQueue() {
      return new Queue(LEGACY_ANONYMOUS_QUEUE);
    }

    @Bean
    Queue anonymousGroupQueue() {
      return new Queue(ANONYMOUS_GROUP_QUEUE);
    }

    @RabbitListener(queues = TEST_QUEUE)
    void consume(String ignored) {
      GlobalTraceUtil.runWithSpan("consumer", () -> {});
    }

    @RabbitListener(queues = METRICS_QUEUE)
    void consumeMetrics(String ignored) {
      metricsMessageConsumed.countDown();
    }

    @RabbitListener(queues = ERROR_QUEUE)
    void consumeError(String ignored) {
      errorMessageConsumed.countDown();
      throw new IllegalStateException("test");
    }

    @RabbitListener(queues = DIRECT_QUEUE, containerFactory = "directFactory")
    void consumeDirect(String ignored) {
      GlobalTraceUtil.runWithSpan("consumer", () -> {});
    }

    @RabbitListener(queues = DIRECT_ERROR_QUEUE, containerFactory = "directFactory")
    void consumeDirectError(String ignored) {
      directErrorMessageConsumed.countDown();
      throw new IllegalStateException("test");
    }
  }
}
