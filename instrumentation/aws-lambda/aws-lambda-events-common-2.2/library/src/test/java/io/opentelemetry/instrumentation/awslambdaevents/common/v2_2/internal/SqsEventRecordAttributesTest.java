/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqsEventRecordAttributesTest {
  @Test
  void noRecords() {
    assertThat(SqsEventRecordAttributes.create(new SQSEvent()).getCommonDestination()).isNull();
    assertThat(SqsEventRecordAttributes.create(event(emptyList())).getCommonDestination()).isNull();
  }

  @Test
  void sameQueue() {
    SQSMessage message1 = message("message1", "arn:aws:sqs:us-east-2:123456789012:queue1");
    SQSMessage message2 = message("message2", "arn:aws:sqs:us-east-2:123456789012:queue1");

    SqsEventRecordAttributes attributes =
        SqsEventRecordAttributes.create(event(asList(message1, message2)));

    assertThat(attributes.getCommonDestination()).isEqualTo("queue1");
    assertThat(attributes.getLinkAttributes(message1))
        .isEqualTo(Attributes.of(MESSAGING_MESSAGE_ID, "message1"));
    assertThat(attributes.getLinkAttributes(message2))
        .isEqualTo(Attributes.of(MESSAGING_MESSAGE_ID, "message2"));
  }

  @Test
  void differentQueues() {
    SQSMessage message1 = message("message1", "arn:aws:sqs:us-east-2:123456789012:queue1");
    SQSMessage message2 = message("message2", "arn:aws:sqs:us-east-2:123456789012:queue2");

    SqsEventRecordAttributes attributes =
        SqsEventRecordAttributes.create(event(asList(message1, message2)));

    assertThat(attributes.getCommonDestination()).isNull();
    assertThat(attributes.getLinkAttributes(message1))
        .isEqualTo(
            Attributes.of(MESSAGING_MESSAGE_ID, "message1", MESSAGING_DESTINATION_NAME, "queue1"));
    assertThat(attributes.getLinkAttributes(message2))
        .isEqualTo(
            Attributes.of(MESSAGING_MESSAGE_ID, "message2", MESSAGING_DESTINATION_NAME, "queue2"));
  }

  @Test
  void missingEventSourceArn() {
    SQSMessage message1 = message("message1", "arn:aws:sqs:us-east-2:123456789012:queue1");
    SQSMessage message2 = message("message2", null);

    SqsEventRecordAttributes attributes =
        SqsEventRecordAttributes.create(event(asList(message1, message2)));

    assertThat(attributes.getCommonDestination()).isNull();
    assertThat(attributes.getLinkAttributes(message1))
        .isEqualTo(
            Attributes.of(MESSAGING_MESSAGE_ID, "message1", MESSAGING_DESTINATION_NAME, "queue1"));
    assertThat(attributes.getLinkAttributes(message2))
        .isEqualTo(Attributes.of(MESSAGING_MESSAGE_ID, "message2"));
  }

  @Test
  void allEventSourceArnsMissing() {
    SQSMessage message1 = message("message1", null);
    SQSMessage message2 = message("message2", null);

    SqsEventRecordAttributes attributes =
        SqsEventRecordAttributes.create(event(asList(message1, message2)));

    assertThat(attributes.getCommonDestination()).isNull();
    assertThat(attributes.getLinkAttributes(message1))
        .isEqualTo(Attributes.of(MESSAGING_MESSAGE_ID, "message1"));
  }

  @Test
  void missingMessageId() {
    SQSMessage message = message(null, "arn:aws:sqs:us-east-2:123456789012:queue1");

    SqsEventRecordAttributes attributes = SqsEventRecordAttributes.create(event(asList(message)));

    assertThat(attributes.getCommonDestination()).isEqualTo("queue1");
    assertThat(attributes.getLinkAttributes(message)).isEqualTo(Attributes.empty());
  }

  private static SQSEvent event(List<SQSMessage> records) {
    SQSEvent event = new SQSEvent();
    event.setRecords(records);
    return event;
  }

  private static SQSMessage message(String messageId, String eventSourceArn) {
    SQSMessage message = new SQSMessage();
    message.setMessageId(messageId);
    message.setEventSourceArn(eventSourceArn);
    return message;
  }
}
