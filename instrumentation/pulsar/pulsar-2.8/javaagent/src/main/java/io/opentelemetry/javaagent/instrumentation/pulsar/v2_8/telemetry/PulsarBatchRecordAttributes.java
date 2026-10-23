/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;

/**
 * Splits the attributes describing the individual messages of a batch between the batch span and
 * the links describing the individual messages. Attributes belong on the batch span when they are
 * shared by every message and meaningful without attributes that vary. In particular, a common
 * partition id belongs on the links when the destination varies because partition ids are only
 * unique within a destination.
 */
class PulsarBatchRecordAttributes {

  private static final PulsarMessagingAttributesGetter messagingAttributesGetter =
      new PulsarMessagingAttributesGetter();

  private boolean initialized;
  @Nullable private String commonDestination;
  @Nullable private String commonPartitionId;
  private boolean destinationVaries;
  private boolean partitionVaries;

  static PulsarBatchRecordAttributes create(Messages<?> messages) {
    PulsarBatchRecordAttributes attributes = new PulsarBatchRecordAttributes();
    for (Message<?> message : messages) {
      attributes.accept(message.getTopicName());
    }
    return attributes;
  }

  private PulsarBatchRecordAttributes() {}

  private void accept(String topicName) {
    String destination = BasePulsarRequest.destination(topicName);
    String partitionId = BasePulsarRequest.destinationPartitionId(topicName);
    if (!initialized) {
      initialized = true;
      commonDestination = destination;
      commonPartitionId = partitionId;
      return;
    }
    destinationVaries |= !Objects.equals(commonDestination, destination);
    partitionVaries |= !Objects.equals(commonPartitionId, partitionId);
  }

  @Nullable
  String getCommonDestination() {
    return destinationVaries ? null : commonDestination;
  }

  /**
   * Returns the partition id to record on the batch span, if any. A partition id is only unique
   * within a destination name, so it is only meaningful on the batch span when the destination name
   * is recorded there too.
   */
  @Nullable
  String getCommonPartitionId() {
    return destinationVaries || partitionVaries ? null : commonPartitionId;
  }

  Attributes getLinkAttributes(PulsarRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    attributes.put(MESSAGING_MESSAGE_ID, messagingAttributesGetter.getMessageId(request, null));
    if (destinationVaries) {
      attributes.put(MESSAGING_DESTINATION_NAME, messagingAttributesGetter.getDestination(request));
    }
    if (destinationVaries || partitionVaries) {
      attributes.put(
          MESSAGING_DESTINATION_PARTITION_ID,
          messagingAttributesGetter.getDestinationPartitionId(request));
    }
    return attributes.build();
  }
}
