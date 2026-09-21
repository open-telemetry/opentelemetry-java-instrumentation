/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Verifies process spans and ordinary messaging metrics for Connect put invocations. */
class KafkaConnectConsumedMessagesTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void failedAttemptAndSuccessfulRetryProduceProcessMetrics() {
    RetryingSinkTask task = new RetryingSinkTask(1);
    SinkRecord record = sinkRecord("failed-retry-topic", 0, 10);

    assertThatThrownBy(() -> task.put(singletonList(record)))
        .isInstanceOf(RetriableException.class);
    task.put(singletonList(record));

    assertProcessMetricsWithConsumedMessages(
        testing,
        INSTRUMENTATION_NAME,
        "failed-retry-topic",
        null,
        "0",
        1,
        1,
        RetriableException.class.getName());
    assertProcessMetricsWithConsumedMessages(
        testing, INSTRUMENTATION_NAME, "failed-retry-topic", null, "0", 1, 1, null);
    assertTotalConsumedMessages(testing, INSTRUMENTATION_NAME, 2);
  }

  @Test
  void batchRetriesKeepFullCardinalityAndLinks() {
    SinkRecord first =
        sinkRecord(
            "mixed-retry-topic", 0, 10, "00-11111111111111111111111111111111-1111111111111111-01");
    SinkRecord second =
        sinkRecord(
            "mixed-retry-topic", 0, 11, "00-22222222222222222222222222222222-2222222222222222-01");
    RetryingSinkTask task = new RetryingSinkTask(1);
    Collection<SinkRecord> records = asList(first, second);
    assertThatThrownBy(() -> task.put(records)).isInstanceOf(RetriableException.class);
    task.put(records);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process mixed-retry-topic"
                                : "mixed-retry-topic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .satisfies(
                            spanData -> {
                              assertRecordLinks(spanData.getLinks());
                              assertThat(
                                      spanData.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT))
                                  .isEqualTo(2);
                            })),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process mixed-retry-topic"
                                : "mixed-retry-topic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .satisfies(
                            spanData -> {
                              assertRecordLinks(spanData.getLinks());
                              assertThat(
                                      spanData.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT))
                                  .isEqualTo(2);
                            })));

    assertProcessMetricsWithConsumedMessages(
        testing,
        INSTRUMENTATION_NAME,
        "mixed-retry-topic",
        null,
        "0",
        1,
        2,
        RetriableException.class.getName());
    assertProcessMetricsWithConsumedMessages(
        testing, INSTRUMENTATION_NAME, "mixed-retry-topic", null, "0", 1, 2, null);
    assertTotalConsumedMessages(testing, INSTRUMENTATION_NAME, 4);
  }

  private static SinkRecord sinkRecord(String topic, int partition, long offset) {
    return new SinkRecord(topic, partition, null, null, null, null, offset);
  }

  private static SinkRecord sinkRecord(
      String topic, int partition, long offset, String traceparent) {
    SinkRecord record = sinkRecord(topic, partition, offset);
    record.headers().addString("traceparent", traceparent);
    return record;
  }

  private static void assertRecordLinks(List<LinkData> links) {
    assertThat(links)
        .satisfiesExactly(
            link ->
                assertRecordLink(link, "11111111111111111111111111111111", "1111111111111111", 10),
            link ->
                assertRecordLink(link, "22222222222222222222222222222222", "2222222222222222", 11));
  }

  private static void assertRecordLink(LinkData link, String traceId, String spanId, long offset) {
    assertThat(link.getSpanContext().getTraceId()).isEqualTo(traceId);
    assertThat(link.getSpanContext().getSpanId()).isEqualTo(spanId);
    assertThat(link.getAttributes())
        .isEqualTo(
            emitStableMessagingSemconv()
                ? Attributes.builder().put(MESSAGING_KAFKA_OFFSET, offset).build()
                : Attributes.empty());
  }

  private static class RetryingSinkTask extends SinkTask {
    private final int failures;
    private int attempts;

    RetryingSinkTask(int failures) {
      this.failures = failures;
    }

    @Override
    public String version() {
      return "test";
    }

    @Override
    public void start(Map<String, String> properties) {}

    @Override
    public void put(Collection<SinkRecord> records) {
      if (attempts++ < failures) {
        throw new RetriableException("retry");
      }
    }

    @Override
    public void stop() {}
  }
}
