/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class SpringRabbit20DirectMessageListenerContainerTest {

  private static final String QUEUE = "springRabbit20DirectQueue";
  private static final String RABBIT_INSTRUMENTATION_NAME = "io.opentelemetry.rabbitmq-2.7";
  private static final String SPRING_INSTRUMENTATION_NAME = "io.opentelemetry.spring-rabbit-1.0";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static GenericContainer<?> rabbitMqContainer;

  @BeforeAll
  static void setUp() {
    rabbitMqContainer =
        new GenericContainer<>("rabbitmq:4.2")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    cleanup.deferAfterAll(rabbitMqContainer::stop);
    rabbitMqContainer.start();
  }

  @Test
  void shouldUseSpringProcessTelemetry() throws InterruptedException {
    CachingConnectionFactory connectionFactory =
        new CachingConnectionFactory(
            rabbitMqContainer.getHost(), rabbitMqContainer.getMappedPort(5672));
    cleanup.deferCleanup(connectionFactory::destroy);

    RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
    rabbitAdmin.declareQueue(new Queue(QUEUE));

    CountDownLatch messageConsumed = new CountDownLatch(1);
    DirectMessageListenerContainer container =
        new DirectMessageListenerContainer(connectionFactory);
    container.setQueueNames(QUEUE);
    container.setAcknowledgeMode(AcknowledgeMode.NONE);
    container.setMessageListener(
        (MessageListener)
            message -> GlobalTraceUtil.runWithSpan("consumer", messageConsumed::countDown));
    cleanup.deferCleanup(container::stop);
    container.start();
    testing.waitForTraces(3);
    testing.clearData();

    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    testing.runWithSpan("parent", () -> rabbitTemplate.convertAndSend(QUEUE, "test"));

    assertThat(messageConsumed.await(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace -> {
          SpanData producerSpan = trace.getSpan(1);
          SpanData processSpan = trace.getSpan(2);
          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("parent"),
              span ->
                  span.hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .satisfies(
                          spanData ->
                              assertThat(spanData.getInstrumentationScopeInfo().getName())
                                  .isEqualTo(RABBIT_INSTRUMENTATION_NAME)),
              span ->
                  span.hasName("process " + QUEUE)
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(producerSpan)
                      .satisfies(
                          spanData ->
                              assertThat(spanData.getInstrumentationScopeInfo().getName())
                                  .isEqualTo(SPRING_INSTRUMENTATION_NAME)),
              span -> span.hasName("consumer").hasParent(processSpan));
        });

    testing.waitAndAssertMetrics(
        SPRING_INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(point -> point.hasCount(1)))));
    testing.waitAndAssertMetrics(
        SPRING_INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum -> sum.hasPointsSatisfying(point -> point.hasValue(1)))));
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(RABBIT_INSTRUMENTATION_NAME)
                    && metric.getName().equals("messaging.process.duration"))
        .isEmpty();
  }
}
