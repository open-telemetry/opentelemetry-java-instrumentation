/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import javax.annotation.Nullable;

/**
 * Jakarta namespace counterpart of {@link IbmMqJmsListenerQmid}. See that class for the full
 * rationale for both the listener/consumer association and the message-keyed receive fallback --
 * only the {@code jakarta.jms} types differ, and only {@link IbmMqJakartaJmsQmid}/{@link
 * IbmMqQmidSupport} are referenced, never {@link IbmMqJmsQmid} (see the warning on that class).
 */
public final class IbmMqJakartaJmsListenerQmid {

  /**
   * Marks a listener as registered via {@code setMessageListener} on a consumer for which no QMID
   * was resolvable at that time. Distinct from "never associated at all" ({@code null}), which is
   * the only case the message-keyed fallback below should apply to -- see the javax-namespace twin
   * of this class, {@link IbmMqJmsListenerQmid}, for the full rationale.
   */
  private static final Object NOT_AVAILABLE = new Object();

  private static final VirtualField<MessageListener, Object> CONSUMER =
      VirtualField.find(MessageListener.class, Object.class);

  private static final VirtualField<Message, String> RECEIVED_QMID =
      VirtualField.find(Message.class, String.class);

  /** At {@code setMessageListener}: remember which consumer this listener was registered on. */
  public static void associate(Object consumer, @Nullable MessageListener listener) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    String qmid = IbmMqJakartaJmsQmid.readQmid(consumer);
    CONSUMER.set(listener, qmid == null ? NOT_AVAILABLE : consumer);
  }

  /**
   * At {@code receive()}/{@code receive(long)}/{@code receiveNoWait()} exit: remember the QMID at
   * the time this message was received, keyed on the message itself. Never touches a span -- see
   * {@link IbmMqJakartaJmsReceiveInstrumentation}.
   */
  public static void captureFromReceive(Object consumer, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || message == null) {
      return;
    }
    String qmid = IbmMqJakartaJmsQmid.readQmid(consumer);
    if (qmid != null) {
      RECEIVED_QMID.set(message, qmid);
    }
  }

  /** At {@code onMessage}: resolve the consumer's current QMID and add it to the process span. */
  public static void stamp(@Nullable MessageListener listener) {
    stamp(listener, null);
  }

  /**
   * As {@link #stamp(MessageListener)}, but additionally falls back to the QMID captured at {@code
   * receive()} time for this exact message when {@code setMessageListener} was never called at all
   * for {@code listener} -- i.e. no association exists, not even one marked unavailable. This
   * covers containers such as Spring's default {@code JmsListenerContainerFactory}, which drives
   * {@code onMessage} by calling {@code receive()} and invoking the listener directly, without ever
   * registering it via {@code setMessageListener}. See {@link IbmMqJmsListenerQmid}'s two-argument
   * {@code stamp} for the javax-namespace twin of this method (kept in sync with it).
   */
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
