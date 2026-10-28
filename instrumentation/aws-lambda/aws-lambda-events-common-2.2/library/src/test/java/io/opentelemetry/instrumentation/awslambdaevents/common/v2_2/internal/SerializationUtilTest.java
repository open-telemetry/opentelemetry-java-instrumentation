/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class SerializationUtilTest {

  private static final String EVENT =
      "{"
          + "\"Records\":[{"
          + "\"messageId\":\"059f36b4-87a3-44ab-83d2-661975830a7d\","
          + "\"receiptHandle\":\"receipt-handle\","
          + "\"body\":\"Test message.\","
          + "\"eventSource\":\"aws:sqs\","
          + "\"eventSourceARN\":\"arn:aws:sqs:us-east-2:123456789012:my-queue\""
          + "}]"
          + "}";

  @Test
  void sqsEventDeserializedFromStringJson() {
    assertSqsEvent(SerializationUtil.fromJson(EVENT, SQSEvent.class));
  }

  @Test
  void sqsEventDeserializedFromInputStreamJson() {
    assertSqsEvent(
        SerializationUtil.fromJson(
            new ByteArrayInputStream(EVENT.getBytes(UTF_8)), SQSEvent.class));
  }

  private static void assertSqsEvent(SQSEvent event) {
    assertThat(event).isNotNull();
    assertThat(event.getRecords()).hasSize(1);
    SQSEvent.SQSMessage record = event.getRecords().get(0);
    assertThat(record.getEventSource()).isEqualTo("aws:sqs");
    assertThat(record.getEventSourceArn()).isEqualTo("arn:aws:sqs:us-east-2:123456789012:my-queue");
    assertThat(record.getMessageId()).isEqualTo("059f36b4-87a3-44ab-83d2-661975830a7d");
    assertThat(record.getBody()).isEqualTo("Test message.");
    assertThat(record.getReceiptHandle()).isEqualTo("receipt-handle");
  }
}
