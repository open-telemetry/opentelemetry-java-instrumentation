/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitInstrumenterHelper.RABBITMQ_COMMAND;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Date;
import javax.annotation.Nullable;

class RabbitDeliveryExperimentalAttributesExtractor
    implements AttributesExtractor<DeliveryRequest, Void> {
  private static final AttributeKey<Long> RABBITMQ_QUEUE_TIME =
      AttributeKey.longKey("rabbitmq.record.queue_time_ms");

  @Override
  public void onStart(AttributesBuilder attributes, DeliveryRequest request) {
    Date timestamp = request.getProperties().getTimestamp();
    if (timestamp != null) {
      // this will be set if the sender sets the timestamp,
      // or if a plugin is installed on the rabbitmq broker
      long produceTimeMillis = timestamp.getTime();
      set(
          attributes,
          RABBITMQ_QUEUE_TIME,
          Math.max(0L, System.currentTimeMillis() - produceTimeMillis));
    }

    set(attributes, RABBITMQ_COMMAND, "basic.deliver");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      DeliveryRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
