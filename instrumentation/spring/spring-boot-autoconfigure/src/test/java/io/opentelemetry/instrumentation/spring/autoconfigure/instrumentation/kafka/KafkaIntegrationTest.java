/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.kafka;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class KafkaIntegrationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static KafkaContainer kafka;

  private ApplicationContextRunner contextRunner;

  @BeforeAll
  static void setUpKafka() {
    kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.10"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();
  }

  @AfterAll
  static void tearDownKafka() {
    kafka.stop();
  }

  @BeforeEach
  void setUpContext() {
    contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    KafkaInstrumentationAutoConfiguration.class,
                    TestConfig.class))
            .withBean("openTelemetry", OpenTelemetry.class, testing::getOpenTelemetry)
            .withPropertyValues(
                "spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.linger-ms=10",
                "spring.kafka.listener.idle-between-polls=1000",
                "spring.kafka.producer.transaction-id-prefix=test-");
  }

  @Test
  void shouldInstrumentProducerAndConsumer() {
    contextRunner.run(KafkaIntegrationTest::runShouldInstrumentProducerAndConsumer);
  }

  // In kafka 2 ops.send is deprecated. We are using it to avoid reflection because kafka 3 also has
  // ops.send, although with different return type.
  @SuppressWarnings({"unchecked", "deprecation"})
  private static void runShouldInstrumentProducerAndConsumer(
      ConfigurableApplicationContext applicationContext) {
    KafkaTemplate<String, String> kafkaTemplate = applicationContext.getBean(KafkaTemplate.class);

    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                ops.send("testTopic", "10", "testSpan");
                return 0;
              });
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName("testTopic publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "testTopic"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("producer")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                                AbstractStringAssert::isNotEmpty),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                AbstractLongAssert::isNotNegative),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                span ->
                    span.hasName("testTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "testTopic"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                AbstractLongAssert::isNotNegative),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                                AbstractStringAssert::isNotEmpty),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                AbstractLongAssert::isNotNegative),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testListener"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @Configuration
  static class TestConfig {

    @Bean
    public NewTopic testTopic() {
      return TopicBuilder.name("testTopic").partitions(1).replicas(1).build();
    }

    @KafkaListener(id = "testListener", topics = "testTopic")
    public void listener(ConsumerRecord<String, String> record) {
      testing.runWithSpan("consumer", () -> {});
    }
  }
}
