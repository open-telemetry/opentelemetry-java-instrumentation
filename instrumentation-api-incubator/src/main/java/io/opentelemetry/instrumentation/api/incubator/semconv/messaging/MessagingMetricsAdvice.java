/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import java.util.ArrayList;
import java.util.List;

final class MessagingMetricsAdvice {
  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0));

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<Boolean> MESSAGING_DESTINATION_ANONYMOUS =
      AttributeKey.booleanKey("messaging.destination.anonymous");
  private static final AttributeKey<Boolean> MESSAGING_DESTINATION_TEMPORARY =
      AttributeKey.booleanKey("messaging.destination.temporary");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_SUBSCRIPTION_NAME =
      AttributeKey.stringKey("messaging.destination.subscription.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  private static final AttributeKey<String> MESSAGING_DESTINATION_TEMPLATE =
      AttributeKey.stringKey("messaging.destination.template");

  private static final List<AttributeKey<?>> OLD_ATTRIBUTES =
      asList(
          MESSAGING_SYSTEM,
          MESSAGING_DESTINATION_NAME,
          MESSAGING_OPERATION,
          MESSAGING_DESTINATION_PARTITION_ID,
          MESSAGING_DESTINATION_TEMPLATE,
          ERROR_TYPE,
          SERVER_PORT,
          SERVER_ADDRESS);

  private static final List<AttributeKey<?>> CLIENT_OPERATION_DURATION_ATTRIBUTES =
      buildAttributes(true, true);
  private static final List<AttributeKey<?>> SENT_MESSAGES_ATTRIBUTES =
      buildAttributes(false, false);
  private static final List<AttributeKey<?>> CONSUMED_MESSAGES_ATTRIBUTES =
      buildAttributes(true, false);
  private static final List<AttributeKey<?>> PROCESS_DURATION_ATTRIBUTES =
      buildAttributes(true, false);

  private static List<AttributeKey<?>> buildAttributes(
      boolean includeConsumerAttributes, boolean includeOperationType) {
    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(MESSAGING_OPERATION_NAME);
    attributes.add(MESSAGING_SYSTEM);
    attributes.add(ERROR_TYPE);
    if (includeConsumerAttributes) {
      attributes.add(MESSAGING_CONSUMER_GROUP_NAME);
    }
    attributes.add(MESSAGING_DESTINATION_NAME);
    if (includeConsumerAttributes) {
      attributes.add(MESSAGING_DESTINATION_SUBSCRIPTION_NAME);
    }
    attributes.add(MESSAGING_DESTINATION_TEMPLATE);
    if (includeOperationType) {
      attributes.add(MESSAGING_OPERATION_TYPE);
    }
    attributes.add(SERVER_ADDRESS);
    attributes.add(MESSAGING_DESTINATION_PARTITION_ID);
    attributes.add(SERVER_PORT);
    return unmodifiableList(attributes);
  }

  static Attributes filterAttributes(Attributes attributes) {
    if (attributes.get(MESSAGING_DESTINATION_TEMPLATE) == null
        && !Boolean.TRUE.equals(attributes.get(MESSAGING_DESTINATION_ANONYMOUS))
        && !Boolean.TRUE.equals(attributes.get(MESSAGING_DESTINATION_TEMPORARY))) {
      return attributes;
    }
    return attributes.toBuilder().remove(MESSAGING_DESTINATION_NAME).build();
  }

  static void applyOldDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(OLD_ATTRIBUTES);
  }

  static void applyClientOperationDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(CLIENT_OPERATION_DURATION_ATTRIBUTES);
  }

  static void applyProcessDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(PROCESS_DURATION_ATTRIBUTES);
  }

  static void applyOldMessagesAdvice(LongCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongCounterBuilder)) {
      return;
    }
    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(OLD_ATTRIBUTES);
  }

  static void applySentMessagesAdvice(LongCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongCounterBuilder)) {
      return;
    }
    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(SENT_MESSAGES_ATTRIBUTES);
  }

  static void applyConsumedMessagesAdvice(LongCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongCounterBuilder)) {
      return;
    }
    ((ExtendedLongCounterBuilder) builder).setAttributesAdvice(CONSUMED_MESSAGES_ATTRIBUTES);
  }

  private MessagingMetricsAdvice() {}
}
