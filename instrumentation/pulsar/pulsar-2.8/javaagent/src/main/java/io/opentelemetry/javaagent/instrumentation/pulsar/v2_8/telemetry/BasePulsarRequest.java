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
   * Returns the value to use for {@code messaging.destination.name}. The stable messaging semantic
   * conventions model the partition separately, in {@code messaging.destination.partition.id},
   * which is defined as being unique within the destination name, so the destination name must not
   * embed the {@code -partition-N} suffix.
   */
  static String destination(String topicName) {
    return emitStableMessagingSemconv()
        ? TopicName.get(topicName).getPartitionedTopicName()
        : topicName;
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
