/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import javax.annotation.Nullable;
import org.apache.camel.Message;

public class CamelMessageTelemetry {

  private static final MessagingTelemetryCarrier<Message> messageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(Message.class, MessagingTelemetrySignals.class));
  private static final VirtualField<Message, JmsMessageDeliveryState> JMS_DELIVERY_STATE =
      VirtualField.find(Message.class, JmsMessageDeliveryState.class);

  public static MessagingTelemetryCarrier<Message> messageTelemetry() {
    return messageTelemetry;
  }

  @Nullable
  public static JmsMessageDeliveryState getJmsDeliveryState(Message message) {
    return JMS_DELIVERY_STATE.get(message);
  }

  private CamelMessageTelemetry() {}
}
