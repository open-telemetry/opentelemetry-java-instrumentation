/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.instrumentation.api.internal.SpanKey;
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
    IbmMqConsumerHolder holder = CONSUMER.get(listener);
    if (holder != null && holder.isAmbiguous()) {
      // Ambiguity is sticky: a third registration on this listener must not resurrect
      // attribution just because it happens to agree with one of the earlier two -- an earlier
      // consumer bound to the queue manager this registration disagreed with can still deliver.
      return;
    }
    Object existingConsumer = holder == null ? null : holder.consumer();
    if (existingConsumer != null && existingConsumer != consumer) {
      // The discriminator is the QMID VALUE, not consumer identity: Spring's
      // DefaultMessageListenerContainer with concurrency greater than 1 legitimately shares one
      // listener bean across several consumer instances on the SAME queue manager, and that
      // common case must keep its attribute, so a mere identity change is not itself ambiguous.
      String existingQmid = IbmMqJakartaJmsQmid.readQmid(existingConsumer);
      String newQmid = IbmMqJakartaJmsQmid.readQmid(consumer);
      if (existingQmid != null && newQmid != null && !existingQmid.equals(newQmid)) {
        CONSUMER.set(listener, IbmMqConsumerHolder.ambiguous());
        return;
      }
    }
    // Stored otherwise, even when the QMID cannot be read right now: overwriting replaces a
    // stale association from a previous registration, and a weak reference to the consumer costs
    // nothing to hold, so a later delivery can retry the read instead of being permanently
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
    // Per-message state wins over the listener association: it is captured at the exact
    // receive() call that produced this message, so it cannot go stale the way the
    // association can (e.g. a listener detached via setMessageListener(null) -- which never
    // clears CONSUMER -- and later driven by a different consumer's receive() call, or a
    // Spring-style container that dispatches via receive() plus direct invocation without ever
    // calling setMessageListener). RECEIVED_QMID is only ever populated from a receive() call
    // on a genuine IBM MQ consumer, so the system value is known here too.
    if (message != null) {
      IbmMqQmid qmid = RECEIVED_QMID.get(message);
      if (qmid != null) {
        IbmMqQmidSupport.stampMessagingSystem(SpanKey.CONSUMER_PROCESS);
        IbmMqQmidSupport.stampMessagingSpan(SpanKey.CONSUMER_PROCESS, qmid.value());
        return;
      }
    }
    IbmMqConsumerHolder holder = CONSUMER.get(listener);
    Object consumer = holder == null ? null : holder.consumer();
    if (consumer != null) {
      String qmid = IbmMqJakartaJmsQmid.readQmid(consumer);
      if (qmid != null) {
        IbmMqQmidSupport.stampMessagingSystem(SpanKey.CONSUMER_PROCESS);
        IbmMqQmidSupport.stampMessagingSpan(SpanKey.CONSUMER_PROCESS, qmid);
      }
    }
  }

  private IbmMqJakartaJmsListenerQmid() {}
}
