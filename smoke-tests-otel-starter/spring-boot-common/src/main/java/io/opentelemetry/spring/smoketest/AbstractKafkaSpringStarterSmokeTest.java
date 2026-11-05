/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

abstract class AbstractKafkaSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired protected KafkaTemplate<String, String> kafkaTemplate;

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void shouldInstrumentProducerAndConsumer() {
    runProducerTest(
        () -> {
          testing.runWithSpan(
              "producer",
              () -> {
                kafkaTemplate.executeInTransaction(
                    ops -> {
                      // return type is incompatible between Spring Boot 2 and 3
                      try {
                        ops.getClass()
                            .getDeclaredMethod("send", String.class, Object.class, Object.class)
                            .invoke(ops, "testTopic", "10", "testSpan");
                      } catch (Exception e) {
                        throw new IllegalStateException(e);
                      }
                      return 0;
                    });
              });

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("producer"),
                      span ->
                          span.hasName("send testTopic")
                              .hasKind(SpanKind.PRODUCER)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(stringKey("messaging.system.name"), "kafka"),
                                  equalTo(MESSAGING_DESTINATION_NAME, "testTopic"),
                                  equalTo(MESSAGING_OPERATION_NAME, "send"),
                                  equalTo(MESSAGING_OPERATION_TYPE, "send"),
                                  satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("producer")),
                                  satisfies(
                                      MESSAGING_DESTINATION_PARTITION_ID,
                                      AbstractStringAssert::isNotEmpty),
                                  satisfies(
                                      longKey("kafka.offset"), AbstractLongAssert::isNotNegative),
                                  equalTo(stringKey("kafka.message.key"), "10"),
                                  satisfies(
                                      stringKey("kafka.cluster.id"),
                                      AbstractStringAssert::isNotEmpty)),
                      span ->
                          span.hasName("process testTopic")
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(1))
                              .hasAttributesSatisfying(
                                  equalTo(stringKey("messaging.system.name"), "kafka"),
                                  equalTo(MESSAGING_DESTINATION_NAME, "testTopic"),
                                  equalTo(MESSAGING_OPERATION_NAME, "process"),
                                  equalTo(MESSAGING_OPERATION_TYPE, "process"),
                                  satisfies(MESSAGING_MESSAGE_BODY_SIZE, val -> val.isNull()),
                                  satisfies(
                                      MESSAGING_DESTINATION_PARTITION_ID,
                                      AbstractStringAssert::isNotEmpty),
                                  satisfies(
                                      longKey("kafka.offset"), AbstractLongAssert::isNotNegative),
                                  equalTo(stringKey("kafka.message.key"), "10"),
                                  equalTo(MESSAGING_CONSUMER_GROUP_NAME, "testListener"),
                                  satisfies(
                                      longKey("kafka.record.queue_time_ms"),
                                      AbstractLongAssert::isNotNegative),
                                  satisfies(
                                      MESSAGING_CLIENT_ID, val -> val.startsWith("consumer"))),
                      span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
        });
  }

  protected void runProducerTest(Runnable runnable) {
    runnable.run();
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
