/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import javax.jms.MessageListener;

/**
 * Carries the IBM MQ queue manager identifier from the point where a {@link MessageListener} is
 * registered to the point where it is invoked.
 *
 * <p>Why the two-step: inside {@code onMessage} the generic JMS instrumentation has already opened
 * and made current a process span, so the span is writable — but the only objects in scope there are
 * the application's own listener and the message, neither of which carries a QMID. The consumer
 * (which does) is only in scope at {@code setMessageListener}. A {@link VirtualField} keyed on the
 * listener bridges the two without a thread local, and without assuming anything about advice
 * ordering, because registration always happens strictly before delivery.
 */
public final class IbmMqJmsListenerQmid {

  private static final VirtualField<MessageListener, String> QMID =
      VirtualField.find(MessageListener.class, String.class);

  /** At {@code setMessageListener}: remember the consumer's QMID against the listener instance. */
  public static void remember(Object consumer, @Nullable MessageListener listener) {
    if (!IbmMqJmsQmid.enabled() || listener == null) {
      return;
    }
    String qmid = IbmMqJmsQmid.readQmid(consumer);
    if (qmid != null) {
      QMID.set(listener, qmid);
    }
  }

  /** At {@code onMessage}: the JMS process span is current, so stamp it. */
  public static void stamp(@Nullable MessageListener listener) {
    if (!IbmMqJmsQmid.enabled() || listener == null) {
      return;
    }
    String qmid = QMID.get(listener);
    if (qmid != null) {
      IbmMqJmsQmid.stampCurrentSpan(qmid);
    }
  }

  private IbmMqJmsListenerQmid() {}
}
