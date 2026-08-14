/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Single pass over the records of an {@link SQSEvent} that determines which attribute values are
 * shared by all records, and which vary and therefore belong on the individual span links.
 */
final class SqsEventRecordAttributes {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");

  private boolean initialized;
  @Nullable private String commonDestination;
  private boolean destinationVaries;

  static SqsEventRecordAttributes create(SQSEvent event) {
    SqsEventRecordAttributes attributes = new SqsEventRecordAttributes();
    List<SQSMessage> records = event.getRecords();
    if (records != null) {
      for (SQSMessage record : records) {
        attributes.accept(record);
      }
    }
    return attributes;
  }

  private SqsEventRecordAttributes() {}

  /** Returns the queue name shared by all records, or {@code null} if it varies across records. */
  @Nullable
  String getCommonDestination() {
    return destinationVaries ? null : commonDestination;
  }

  Attributes getLinkAttributes(SQSMessage record) {
    AttributesBuilder attributes = Attributes.builder();
    attributes.put(MESSAGING_MESSAGE_ID, record.getMessageId());
    if (destinationVaries) {
      attributes.put(MESSAGING_DESTINATION_NAME, SqsAttributesGetter.queueName(record));
    }
    return attributes.build();
  }

  private void accept(SQSMessage record) {
    String destination = SqsAttributesGetter.queueName(record);
    if (!initialized) {
      initialized = true;
      commonDestination = destination;
      return;
    }
    destinationVaries |= !Objects.equals(commonDestination, destination);
  }
}
