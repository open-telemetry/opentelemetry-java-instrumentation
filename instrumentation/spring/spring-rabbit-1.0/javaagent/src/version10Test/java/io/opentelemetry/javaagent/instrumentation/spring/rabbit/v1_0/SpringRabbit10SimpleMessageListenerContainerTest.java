/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
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
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class SpringRabbit10SimpleMessageListenerContainerTest {

  private static final String QUEUE = "springRabbit10SimpleQueue";
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
    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setQueueNames(QUEUE);
    container.setAcknowledgeMode(AcknowledgeMode.NONE);
    container.setMessageListener(
        (MessageListener)
            message -> GlobalTraceUtil.runWithSpan("consumer", messageConsumed::countDown));
    cleanup.deferCleanup(container::stop);
    container.start();

    // RabbitMQ client 2.5.0 is not instrumented, so inject propagation explicitly.
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    testing.runWithSpan(
        "parent",
        () ->
            rabbitTemplate.convertAndSend(
                QUEUE,
                "test",
                message -> {
                  testing
                      .getOpenTelemetry()
                      .getPropagators()
                      .getTextMapPropagator()
                      .inject(
                          Context.current(),
                          message.getMessageProperties(),
                          (properties, key, value) -> properties.setHeader(key, value));
                  return message;
                }));

    assertThat(messageConsumed.await(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace -> {
          SpanData processSpan = trace.getSpan(1);
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasNoParent(),
              span ->
                  span.hasName("process " + QUEUE)
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0))
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
  }
}
