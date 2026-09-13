/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.ibm.msg.client.jakarta.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants;
import javax.annotation.Nullable;

public class IbmMqJakartaJmsQmid {

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

  public static void stampMessagingSpan(Object jmsObject) {
    if (!IbmMqQmidSupport.enabled()) {
      return;
    }
    // Read the QMID first: this method now also runs for listeners re-associated after a
    // transient read failure, which may be re-invoked for a non-IBM-MQ consumer too. Only stamp
    // messaging.system once a genuine QMID was actually read, never before.
    String qmid = readQmid(jmsObject);
    if (qmid == null) {
      return;
    }
    IbmMqQmidSupport.stampMessagingSystem();
    IbmMqQmidSupport.stampMessagingSpan(qmid);
  }

  private IbmMqJakartaJmsQmid() {}
}
