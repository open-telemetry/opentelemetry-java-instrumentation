/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.ibm.msg.client.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.wmq.common.CommonConstants;
import javax.annotation.Nullable;

public class IbmMqJmsQmid {

  // Never cached: the resolved properties are refreshed after an automatic client reconnect, which
  // may land on a different queue manager.
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
    // Every caller reaches this method only for an object the type matcher already confirmed is a
    // genuine IBM MQ client type, so the system value is known even when the QMID property below
    // is unavailable.
    IbmMqQmidSupport.stampMessagingSystem();
    String qmid = readQmid(jmsObject);
    if (qmid != null) {
      IbmMqQmidSupport.stampMessagingSpan(qmid);
    }
  }

  private IbmMqJmsQmid() {}
}
