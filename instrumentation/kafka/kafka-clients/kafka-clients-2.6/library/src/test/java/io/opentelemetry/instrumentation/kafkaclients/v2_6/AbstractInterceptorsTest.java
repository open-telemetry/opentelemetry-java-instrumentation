/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
abstract class AbstractInterceptorsTest extends KafkaClientBaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final String greeting = "Hello Kafka!";

  protected abstract KafkaTelemetry kafkaTelemetry();

  protected abstract boolean captureExperimentalSpanAttributes();

  @Override
  public Map<String, Object> producerProps() {
    Map<String, Object> props = super.producerProps();
    props.putAll(kafkaTelemetry().producerInterceptorConfigProperties());
    props.putAll(kafkaTelemetry().metricConfigProperties());
    return props;
  }

  @Override
  public Map<String, Object> consumerProps() {
    Map<String, Object> props = super.consumerProps();
    props.putAll(kafkaTelemetry().consumerInterceptorConfigProperties());
    props.putAll(kafkaTelemetry().metricConfigProperties());
    return props;
  }

  @Test
  void testInterceptors() throws InterruptedException {
    testing.runWithSpan(
        "parent",
        () -> {
          ProducerRecord<Integer, String> producerRecord =
              new ProducerRecord<>(SHARED_TOPIC, greeting);
          producerRecord
              .headers()
              // add header to test capturing header value as span attribute
              .add("Test-Message-Header", "test".getBytes(UTF_8))
              .add("Uncaptured-Header", "password".getBytes(UTF_8))
              // adding baggage header in w3c baggage format
              .add("baggage", "test-baggage-key-1=test-baggage-value-1".getBytes(UTF_8))
              .add("baggage", "test-baggage-key-2=test-baggage-value-2".getBytes(UTF_8));
          producer.send(
              producerRecord,
              (meta, ex) -> {
                if (ex == null) {
                  testing.runWithSpan("producer callback", () -> {});
                } else {
                  testing.runWithSpan("producer exception: " + ex, () -> {});
                }
              });
        });

    awaitUntilConsumerIsReady();
    // check that the message was received
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isEqualTo(greeting);
      assertThat(record.key()).isNull();
      testing.runWithSpan("process child", () -> {});
    }

    assertTraces();
  }

  void assertTraces() {
    AtomicReference<SpanContext> producerSpanContext = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", SHARED_TOPIC + " receive", "producer callback"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          publishAttributes(captureExperimentalSpanAttributes())));
          SpanContext spanContext = trace.getSpan(1).getSpanContext();
          producerSpanContext.set(
              SpanContext.createFromRemoteParent(
                  spanContext.getTraceId(),
                  spanContext.getSpanId(),
                  spanContext.getTraceFlags(),
                  spanContext.getTraceState()));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinksSatisfying(links -> assertThat(links).isEmpty())
                        .hasAttributesSatisfyingExactly(receiveAttributes()),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpanContext.get()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(captureExperimentalSpanAttributes())),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))),
        // ideally we'd want producer callback to be part of the main trace, we just aren't able to
        // instrument that
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("producer callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  private static List<AttributeAssertion> publishAttributes(boolean experimental) {
    return asList(
        equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")),
        equalTo(MESSAGING_SYSTEM, "kafka"),
        equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
        equalTo(MESSAGING_OPERATION, "publish"),
        satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("producer")),
        satisfies(
            stringKey("messaging.kafka.bootstrap.servers"),
            val -> {
              if (experimental) {
                val.matches("^localhost:\\d+(,localhost:\\d+)*$");
              }
            }));
  }

  private static List<AttributeAssertion> receiveAttributes() {
    return asList(
        equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")),
        equalTo(MESSAGING_SYSTEM, "kafka"),
        equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
        equalTo(MESSAGING_OPERATION, "receive"),
        equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
        satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("consumer")),
        equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
  }

  private static List<AttributeAssertion> processAttributes(boolean experimental) {
    return asList(
        equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")),
        equalTo(MESSAGING_SYSTEM, "kafka"),
        equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
        equalTo(MESSAGING_OPERATION, "process"),
        equalTo(MESSAGING_MESSAGE_BODY_SIZE, greeting.getBytes(UTF_8).length),
        satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
        satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
        equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
        satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("consumer")),
        satisfies(
            longKey("kafka.record.queue_time_ms"),
            val -> {
              if (experimental) {
                val.isNotNegative();
              }
            }));
  }
}
