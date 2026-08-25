/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import javax.annotation.Nullable;

public final class IbmMqJakartaJmsListenerQmid {

  private static final Object NOT_AVAILABLE = new Object();

  private static final VirtualField<MessageListener, Object> CONSUMER =
      VirtualField.find(MessageListener.class, Object.class);

  private static final VirtualField<Message, String> RECEIVED_QMID =
      VirtualField.find(Message.class, String.class);

  public static void associate(Object consumer, @Nullable MessageListener listener) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    String qmid = IbmMqJakartaJmsQmid.readQmid(consumer);
    CONSUMER.set(listener, qmid == null ? NOT_AVAILABLE : consumer);
  }

  public static void captureFromReceive(Object consumer, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || message == null) {
      return;
    }
    String qmid = IbmMqJakartaJmsQmid.readQmid(consumer);
    if (qmid != null) {
      RECEIVED_QMID.set(message, qmid);
    }
  }

  public static void stamp(@Nullable MessageListener listener) {
    stamp(listener, null);
  }

  public static void stamp(@Nullable MessageListener listener, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    Object consumer = CONSUMER.get(listener);
    if (consumer == NOT_AVAILABLE) {
      return;
    }
    if (consumer != null) {
      IbmMqJakartaJmsQmid.stampMessagingSpan(consumer);
      return;
    }
    if (message == null) {
      return;
    }
    String qmid = RECEIVED_QMID.get(message);
    if (qmid != null) {
      IbmMqQmidSupport.stampMessagingSpan(qmid);
    }
  }

  private IbmMqJakartaJmsListenerQmid() {}
}
