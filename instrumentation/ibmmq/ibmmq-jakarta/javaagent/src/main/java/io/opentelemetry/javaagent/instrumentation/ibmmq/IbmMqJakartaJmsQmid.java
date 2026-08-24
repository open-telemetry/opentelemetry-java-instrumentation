/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.ibm.msg.client.jakarta.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants;
import javax.annotation.Nullable;

/**
 * Jakarta namespace counterpart of {@link IbmMqJmsQmid}, for applications using IBM's {@code
 * com.ibm.mq.jakarta.client} provider instead of {@code com.ibm.mq.allclient}.
 *
 * <p>IBM ships {@code com.ibm.msg.client.jakarta.jms.*} as a package-renamed but otherwise
 * structurally identical mirror of {@code com.ibm.msg.client.jms.*}: same simple names, same method
 * surface, no shared supertype with the javax side. This class must never reference {@link
 * IbmMqJmsQmid} or any other javax namespace class: that class's own {@code readQmid} references
 * {@code com.ibm.msg.client.jms.*} types, and muzzle collects references per class from the whole
 * class file, so referencing it here would drag a javax-only type into this jakarta {@code
 * InstrumentationModule}'s reference set and fail muzzle validation on every jakarta-only
 * classpath. The only class either namespace may reference from the other side is {@link
 * IbmMqQmidSupport}, which imports no MQ or JMS type at all.
 */
public final class IbmMqJakartaJmsQmid {

  /**
   * Reads the QMID from an IBM MQ jakarta JMS object (connection, session, producer or consumer).
   * Returns null for a non-IBM object, when no QMID is available, or on any failure. See {@link
   * IbmMqJmsQmid#readQmid(Object)} for the full rationale, identical here.
   */
  @Nullable
  public static String readQmid(Object jmsObject) {
    if (!(jmsObject instanceof JmsReadablePropertyContext)) {
      return null;
    }
    try {
      String qmid =
          ((JmsReadablePropertyContext) jmsObject)
              .getStringProperty(CommonConstants.WMQ_RESOLVED_QUEUE_MANAGER_ID);
      if (qmid == null) {
        return null;
      }
      // MQCA_Q_MGR_IDENTIFIER is a fixed 48-byte, space-padded field.
      qmid = qmid.trim();
      return qmid.isEmpty() ? null : qmid;
    } catch (Throwable t) {
      // Enrichment is best-effort and must never affect the instrumented application.
      return null;
    }
  }

  /**
   * Reads the QMID from the given IBM MQ jakarta JMS object and adds it to the current messaging
   * span.
   */
  public static void stampMessagingSpan(Object jmsObject) {
    if (!IbmMqQmidSupport.enabled()) {
      return;
    }
    String qmid = readQmid(jmsObject);
    if (qmid != null) {
      IbmMqQmidSupport.stampMessagingSpan(qmid);
    }
  }

  private IbmMqJakartaJmsQmid() {}
}
