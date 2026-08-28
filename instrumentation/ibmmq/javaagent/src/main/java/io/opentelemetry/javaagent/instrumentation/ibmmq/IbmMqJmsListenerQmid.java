/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageListener;

public final class IbmMqJmsListenerQmid {

  private static final Object NOT_AVAILABLE = new Object();

  private static final VirtualField<MessageListener, Object> CONSUMER =
      VirtualField.find(MessageListener.class, Object.class);

  // Dedicated value type, not String: the generic JMS instrumentation already owns the
  // (Message, String) virtual-field pair, and pairs are shared across modules.
  private static final VirtualField<Message, IbmMqQmid> RECEIVED_QMID =
      VirtualField.find(Message.class, IbmMqQmid.class);

  public static void associate(Object consumer, @Nullable MessageListener listener) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    // Overwrite unconditionally, so that re-registering a listener on a different (or non-IBM)
    // consumer replaces the previous association rather than keeping a stale one.
    String qmid = IbmMqJmsQmid.readQmid(consumer);
    CONSUMER.set(listener, qmid == null ? NOT_AVAILABLE : consumer);
  }

  public static void captureFromReceive(Object consumer, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || message == null) {
      return;
    }
    String qmid = IbmMqJmsQmid.readQmid(consumer);
    if (qmid != null) {
      RECEIVED_QMID.set(message, new IbmMqQmid(qmid));
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
      IbmMqJmsQmid.stampMessagingSpan(consumer);
      return;
    }
    if (message == null) {
      return;
    }
    IbmMqQmid qmid = RECEIVED_QMID.get(message);
    if (qmid != null) {
      IbmMqQmidSupport.stampMessagingSpan(qmid.value());
    }
  }

  private IbmMqJmsListenerQmid() {}
}
