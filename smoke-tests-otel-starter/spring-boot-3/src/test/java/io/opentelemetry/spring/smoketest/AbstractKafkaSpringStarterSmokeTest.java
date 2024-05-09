/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.spring.smoketest.AbstractSpringStarterSmokeTest;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication;
import io.opentelemetry.spring.smoketest.SpringSmokeOtelConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      SpringSmokeOtelConfiguration.class,
      AbstractKafkaSpringStarterSmokeTest.KafkaConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.consumer.linger-ms=10",
      "spring.kafka.listener.idle-between-polls=1000",
      "spring.kafka.producer.transaction-id-prefix=test-"
    })
abstract class AbstractKafkaSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  // In kafka 2 ops.send is deprecated. We are using it to avoid reflection because kafka 3 also has
  // ops.send, although with different return type.
  @SuppressWarnings({"unchecked", "deprecation"})
  @Test
  void shouldInstrumentProducerAndConsumer() {
    testing.clearAllExportedData(); // ignore data from application startup

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
                        .hasAttributesSatisfying(
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
  public static class KafkaConfig {

    @Autowired OpenTelemetry openTelemetry;

    @Bean
    public NewTopic testTopic() {
      return TopicBuilder.name("testTopic").partitions(1).replicas(1).build();
    }

    @KafkaListener(id = "testListener", topics = "testTopic")
    public void listener(ConsumerRecord<String, String> record) {
      openTelemetry
          .getTracer("consumer", "1.0")
          .spanBuilder("consumer")
          .setSpanKind(SpanKind.CONSUMER)
          .startSpan()
          .end();
    }
  }
}
