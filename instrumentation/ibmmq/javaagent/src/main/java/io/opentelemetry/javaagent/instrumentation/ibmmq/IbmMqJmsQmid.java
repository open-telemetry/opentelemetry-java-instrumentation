/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.ibm.msg.client.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.wmq.common.CommonConstants;
import javax.annotation.Nullable;

/**
 * Reads the IBM MQ Queue Manager Identifier (QMID) and adds it to the messaging span created by the
 * generic JMS instrumentation, for applications on IBM's javax MQ client ({@code
 * com.ibm.mq.allclient}). See {@link IbmMqJakartaJmsQmid} for the jakarta namespace counterpart,
 * and {@link IbmMqQmidSupport} for the logic shared between them.
 *
 * <p>Purely additive: it never creates, ends or otherwise alters a span, and never propagates a
 * failure into the application. It exists because queue manager <i>names</i> are not globally
 * unique in federated architectures, whereas the QMID is.
 *
 * <p>The value is read from IBM's own resolved-connection property, which the MQ client populates
 * locally during {@code MQCONN}. {@code JmsPropertyContext} extends {@code Map}, so this is a local
 * lookup rather than an MQI round trip, and is safe on the message path.
 *
 * <p>This class must never be referenced from {@link IbmMqJakartaJmsQmid} or any other jakarta
 * namespace class: its {@link #readQmid} references {@code com.ibm.msg.client.jms.*} types, and
 * muzzle collects references per class, from the whole class file. A jakarta class referencing this
 * one would drag that javax reference into the jakarta {@code InstrumentationModule}'s reference
 * set and fail muzzle validation on every jakarta-only classpath.
 */
public final class IbmMqJmsQmid {

  /** True when the opt_in attribute has been explicitly enabled. */
  public static boolean enabled() {
    return IbmMqQmidSupport.enabled();
  }

  /**
   * Reads the QMID from an IBM MQ JMS object (connection, session, producer or consumer). Returns
   * null for a non-IBM object, when no QMID is available, or on any failure.
   *
   * <p>Deliberately never cached: {@code WMQConnection}/{@code WMQSession} refresh their resolved
   * properties after an automatic client reconnect, which may land on a different queue manager.
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

  /** Reads the QMID from the given IBM MQ JMS object and adds it to the current messaging span. */
  public static void stampMessagingSpan(Object jmsObject) {
    if (!IbmMqQmidSupport.enabled()) {
      return;
    }
    String qmid = readQmid(jmsObject);
    if (qmid != null) {
      IbmMqQmidSupport.stampMessagingSpan(qmid);
    }
  }

  /** Adds the QMID to the messaging span in the current context, if there is one. */
  public static void stampMessagingSpan(String qmid) {
    IbmMqQmidSupport.stampMessagingSpan(qmid);
  }

  private IbmMqJmsQmid() {}
}
