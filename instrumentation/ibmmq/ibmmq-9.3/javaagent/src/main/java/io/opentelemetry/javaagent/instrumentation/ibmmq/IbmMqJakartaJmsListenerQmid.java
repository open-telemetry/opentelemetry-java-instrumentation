/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import javax.annotation.Nullable;

public class IbmMqJakartaJmsListenerQmid {

  private static final VirtualField<MessageListener, IbmMqConsumerHolder> CONSUMER =
      VirtualField.find(MessageListener.class, IbmMqConsumerHolder.class);

  // Dedicated value type, not String: the generic JMS instrumentation already owns the
  // (Message, String) virtual-field pair, and pairs are shared across modules.
  private static final VirtualField<Message, IbmMqQmid> RECEIVED_QMID =
      VirtualField.find(Message.class, IbmMqQmid.class);

  public static void associate(Object consumer, @Nullable MessageListener listener) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    // Stored unconditionally, even when the QMID cannot be read right now: overwriting replaces
    // a stale association from a previous registration, and a weak reference to the consumer
    // costs nothing to hold, so a later delivery can retry the read instead of being permanently
    // short-circuited by one failed attempt at registration time.
    CONSUMER.set(listener, new IbmMqConsumerHolder(consumer));
  }

  public static void captureFromReceive(Object consumer, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || message == null) {
      return;
    }
    String qmid = IbmMqJakartaJmsQmid.readQmid(consumer);
    if (qmid != null) {
      RECEIVED_QMID.set(message, new IbmMqQmid(qmid));
    }
  }

  public static void stamp(@Nullable MessageListener listener, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    IbmMqConsumerHolder holder = CONSUMER.get(listener);
    Object consumer = holder == null ? null : holder.consumer();
    if (consumer != null) {
      IbmMqJakartaJmsQmid.stampMessagingSpan(consumer);
      return;
    }
    if (message == null) {
      return;
    }
    IbmMqQmid qmid = RECEIVED_QMID.get(message);
    if (qmid != null) {
      // RECEIVED_QMID is only ever populated from a receive() call on a genuine IBM MQ consumer,
      // so the system value is known here too.
      IbmMqQmidSupport.stampMessagingSystem();
      IbmMqQmidSupport.stampMessagingSpan(qmid.value());
    }
  }

  private IbmMqJakartaJmsListenerQmid() {}
}
