/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * Associates an asynchronous {@link MessageListener} with the IBM MQ consumer it was registered on,
 * so the queue manager identifier can be resolved at delivery time. Also carries the QMID forward
 * from a plain {@code receive()} call to whatever later processes the {@link Message} it returned,
 * for consumers that never call {@code setMessageListener} at all -- see {@link
 * IbmMqJmsReceiveInstrumentation}. See {@link IbmMqJakartaJmsListenerQmid} for the jakarta
 * namespace counterpart; the two must never reference each other or each other's MQ/JMS types (see
 * the warning on {@link IbmMqJmsQmid}).
 *
 * <p>Why the association rather than the value (for {@code setMessageListener}): inside {@code
 * onMessage} the generic JMS instrumentation has already opened and made current a process span, so
 * the span is writable -- but the only objects in scope there are the application's listener and
 * the message, neither of which carries a QMID. The consumer, which does, is only in scope at
 * {@code setMessageListener}.
 *
 * <p>The <em>consumer</em> is stored rather than the QMID string for that association, and the
 * identifier is re-read on every delivery. Automatic client reconnect does not re-run {@code
 * setMessageListener}, so caching the string would keep reporting the original queue manager
 * indefinitely after a reconnect resolved to a different one. The message-keyed capture below
 * stores the QMID string directly instead, because the gap between a {@code receive()} call
 * returning and that same message reaching {@code onMessage} is a single, uninterrupted call stack
 * -- there is no realistic window for a reconnect in between.
 */
public final class IbmMqJmsListenerQmid {

  /**
   * Marks a listener as registered via {@code setMessageListener} on a consumer for which no QMID
   * was resolvable at that time (a non-IBM consumer, or an IBM consumer with the property
   * unavailable). Distinct from "never associated at all" ({@code null}), which is the only case
   * the message-keyed fallback below should apply to -- see {@link #stamp(MessageListener,
   * Message)}.
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
    // Overwrite unconditionally, so that re-registering a listener on a different (or non-IBM)
    // consumer replaces the previous association rather than keeping a stale one.
    String qmid = IbmMqJmsQmid.readQmid(consumer);
    CONSUMER.set(listener, qmid == null ? NOT_AVAILABLE : consumer);
  }

  /**
   * At {@code receive()}/{@code receive(long)}/{@code receiveNoWait()} exit: remember the QMID at
   * the time this message was received, keyed on the message itself. This never touches a span: the
   * receive span (if any) is created and ended by the generic JMS instrumentation via {@code
   * startAndEnd} before this advice can run, and is never made current, so it cannot be enriched
   * here or anywhere else -- see {@link IbmMqInstrumentationModule}. This is purely a handoff of a
   * plain value to whichever later advice (e.g. {@code onMessage}) ends up processing this exact
   * message.
   */
  public static void captureFromReceive(Object consumer, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || message == null) {
      return;
    }
    String qmid = IbmMqJmsQmid.readQmid(consumer);
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
   * for {@code listener} -- i.e. no association exists, not even one marked {@link #NOT_AVAILABLE}.
   * This covers containers such as Spring's default {@code JmsListenerContainerFactory}, which
   * drives {@code onMessage} by calling {@code receive()} and invoking the listener directly,
   * without ever registering it via {@code setMessageListener}.
   */
  public static void stamp(@Nullable MessageListener listener, @Nullable Message message) {
    if (!IbmMqQmidSupport.enabled() || listener == null) {
      return;
    }
    Object consumer = CONSUMER.get(listener);
    if (consumer == NOT_AVAILABLE) {
      // setMessageListener WAS called for this listener; preserve today's no-op exactly, with no
      // fallback attempted -- this was never in scope for the setMessageListener path.
      return;
    }
    if (consumer != null) {
      // Re-read rather than cache, so a reconnect to a different queue manager is reflected.
      IbmMqJmsQmid.stampMessagingSpan(consumer);
      return;
    }
    // consumer == null here means setMessageListener was never called for this listener at all.
    if (message == null) {
      return;
    }
    String qmid = RECEIVED_QMID.get(message);
    if (qmid != null) {
      IbmMqQmidSupport.stampMessagingSpan(qmid);
    }
  }

  private IbmMqJmsListenerQmid() {}
}
