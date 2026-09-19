/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaRecordDeliveryState;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import javax.annotation.Nullable;
import org.apache.camel.Message;

public class CamelMessageTelemetry {

  private static final MessagingTelemetryCarrier<Message> messageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(Message.class, MessagingTelemetrySignals.class));
  private static final VirtualField<Message, KafkaRecordDeliveryState> KAFKA_DELIVERY_STATE =
      VirtualField.find(Message.class, KafkaRecordDeliveryState.class);

  public static MessagingTelemetryCarrier<Message> messageTelemetry() {
    return messageTelemetry;
  }

  @Nullable
  public static KafkaRecordDeliveryState getKafkaDeliveryState(Message message) {
    return KAFKA_DELIVERY_STATE.get(message);
  }

  private CamelMessageTelemetry() {}
}
