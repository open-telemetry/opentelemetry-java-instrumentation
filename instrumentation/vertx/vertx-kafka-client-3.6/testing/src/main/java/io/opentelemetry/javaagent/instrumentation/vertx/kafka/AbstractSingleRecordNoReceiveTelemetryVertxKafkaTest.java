/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanEnabledSupplier;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractSingleRecordNoReceiveTelemetryVertxKafkaTest
    extends AbstractVertxKafkaTest {

  private final CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  void setUpTopicAndConsumer() {
    kafkaConsumer.handler(
        record -> {
          assertThat(processSpanEnabledSupplier().getAsBoolean()).isTrue();
          testing().runWithSpan("consumer", () -> {});
          if ("error".equals(record.value())) {
            throw new IllegalArgumentException("boom");
          }
        });

    kafkaConsumer.partitionsAssignedHandler(partitions -> consumerReady.countDown());
    subscribe("testSingleTopic");
  }

  @Test
  void shouldCreateSpansForSingleRecordProcess() throws InterruptedException {
    assertThat(consumerReady.await(30, SECONDS)).isTrue();

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testSingleTopic", "10", "testSpan");
    CountDownLatch sent = new CountDownLatch(1);
    testing().runWithSpan("producer", () -> sendRecord(record, result -> sent.countDown()));
    assertThat(sent.await(30, SECONDS)).isTrue();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("producer"),
                    span ->
                        span.hasName("send testSingleTopic")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(sendAttributes(record)),
                    span -> {
                      span.hasName("process testSingleTopic")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(processAttributes(record));
                      span.hasLinks(LinkData.create(asRemote(trace.getSpan(1).getSpanContext())));
                    },
                    span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
    assertProcessMetricsWithConsumedMessages(
        testing(),
        "io.opentelemetry.vertx-kafka-client-3.6",
        "testSingleTopic",
        hasConsumerGroup() ? "test" : null,
        "0",
        1,
        1,
        null);
  }

  @Test
  void shouldHandleFailureInSingleRecordHandler() throws InterruptedException {
    assertThat(consumerReady.await(30, SECONDS)).isTrue();

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testSingleTopic", "10", "error");
    List<AttributeAssertion> attributes = processAttributes(record);
    attributes.add(equalTo(ERROR_TYPE, IllegalArgumentException.class.getName()));
    CountDownLatch sent = new CountDownLatch(1);
    testing().runWithSpan("producer", () -> sendRecord(record, result -> sent.countDown()));
    assertThat(sent.await(30, SECONDS)).isTrue();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("producer"),
                    span ->
                        span.hasName("send testSingleTopic")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(sendAttributes(record)),
                    span -> {
                      span.hasName("process testSingleTopic")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(1))
                          .hasStatus(StatusData.error())
                          .hasException(new IllegalArgumentException("boom"))
                          .hasAttributesSatisfyingExactly(attributes);
                      span.hasLinks(LinkData.create(asRemote(trace.getSpan(1).getSpanContext())));
                    },
                    span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
    assertProcessMetricsWithConsumedMessages(
        testing(),
        "io.opentelemetry.vertx-kafka-client-3.6",
        "testSingleTopic",
        hasConsumerGroup() ? "test" : null,
        "0",
        1,
        1,
        IllegalArgumentException.class.getName());
  }
}
