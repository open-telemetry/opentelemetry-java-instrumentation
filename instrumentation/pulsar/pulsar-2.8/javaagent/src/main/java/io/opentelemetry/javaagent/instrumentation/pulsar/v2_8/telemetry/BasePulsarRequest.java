/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.common.naming.TopicName;

public class BasePulsarRequest {

  private final String destination;
  @Nullable private final String destinationPartitionId;
  @Nullable private final UrlData urlData;
  @Nullable private final String subscription;

  protected BasePulsarRequest(
      String topicName, @Nullable UrlData urlData, @Nullable String subscription) {
    this.destination = destination(topicName);
    this.destinationPartitionId = destinationPartitionId(topicName);
    this.urlData = urlData;
    this.subscription = subscription;
  }

  /**
   * Returns the value to use for {@code messaging.destination.name}. Under the stable messaging
   * semantic conventions this is the fully qualified topic name without the {@code -partition-N}
   * suffix, e.g. {@code persistent://public/default/my-topic}. The partition is modeled separately,
   * in {@code messaging.destination.partition.id}, which is defined as being unique within the
   * destination name, so the destination name must not embed it. Under the old semantic conventions
   * the topic name is reported as is.
   */
  static String destination(String topicName) {
    if (!emitStableMessagingSemconv()) {
      return topicName;
    }
    // getPartitionedTopicName() strips the "-partition-N" suffix and also expands a topic name that
    // is not fully qualified, e.g. "my-topic" to "persistent://public/default/my-topic". A producer
    // can be created with a short topic name while a consumer always sees the fully qualified form,
    // so expanding here is what lets producer and consumer spans for the same topic agree on the
    // destination name.
    //
    // TopicName.get() throws IllegalArgumentException on a malformed topic name, but one cannot
    // reach this method: producer and consumer topic names are rejected by PulsarClientImpl with
    // InvalidTopicNameException unless TopicName.isValid() accepts them, and TopicName.isValid() is
    // TopicName.get() wrapped in a try/catch.
    return TopicName.get(topicName).getPartitionedTopicName();
  }

  /** Returns the value to use for {@code messaging.destination.partition.id}. */
  @Nullable
  static String destinationPartitionId(String topicName) {
    int partitionIndex = TopicName.getPartitionIndex(topicName);
    return partitionIndex == -1 ? null : String.valueOf(partitionIndex);
  }

  public String getDestination() {
    return destination;
  }

  @Nullable
  public String getDestinationPartitionId() {
    return destinationPartitionId;
  }

  @Nullable
  public UrlData getUrlData() {
    return urlData;
  }

  /** Returns the name of the subscription this message was consumed from, if any. */
  @Nullable
  public String getSubscription() {
    return subscription;
  }
}
