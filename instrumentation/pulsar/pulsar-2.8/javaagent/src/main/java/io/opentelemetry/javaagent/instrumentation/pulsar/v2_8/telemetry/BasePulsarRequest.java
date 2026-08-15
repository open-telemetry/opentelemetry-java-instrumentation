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
    // A producer can be created with a short topic name while a consumer always sees the fully
    // qualified form, so expanding here lets producer and consumer spans for the same topic agree
    // on the destination name.
    return TopicName.get(stripPartitionSuffix(topicName)).toString();
  }

  /**
   * Returns {@code topicName} without its {@code -partition-N} suffix, or {@code topicName} when it
   * does not have one.
   */
  static String stripPartitionSuffix(String topicName) {
    int suffixIndex = partitionSuffixIndex(topicName);
    if (suffixIndex == -1) {
      return topicName;
    }
    return topicName.substring(0, suffixIndex);
  }

  /** Returns the value to use for {@code messaging.destination.partition.id}. */
  @Nullable
  static String destinationPartitionId(String topicName) {
    int suffixIndex = partitionSuffixIndex(topicName);
    return suffixIndex == -1
        ? null
        : topicName.substring(suffixIndex + TopicName.PARTITIONED_TOPIC_SUFFIX.length());
  }

  private static int partitionSuffixIndex(String topicName) {
    int partitionIndex = TopicName.getPartitionIndex(topicName);
    if (partitionIndex == -1) {
      return -1;
    }
    int suffixIndex = topicName.lastIndexOf(TopicName.PARTITIONED_TOPIC_SUFFIX);
    // pulsar 2.8 reads the partition index from the last '-' instead of from the last
    // "-partition-", so it reports e.g. "my-topic-partition-key-1" as partition 1, and it accepts a
    // zero padded suffix that pulsar 2.9+ rejects; only recognize a suffix that is exactly the
    // partition index
    if (suffixIndex == -1
        || !topicName
            .substring(suffixIndex + TopicName.PARTITIONED_TOPIC_SUFFIX.length())
            .equals(String.valueOf(partitionIndex))) {
      return -1;
    }
    return suffixIndex;
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
