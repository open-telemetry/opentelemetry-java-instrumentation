/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static java.util.logging.Level.FINE;

import com.ibm.msg.client.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.wmq.common.CommonConstants;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class IbmMqJmsQmid {

  private static final Logger logger = Logger.getLogger(IbmMqJmsQmid.class.getName());

  // Never cached: the resolved properties are refreshed after an automatic client reconnect, which
  // may land on a different queue manager.
  @Nullable
  public static String readQmid(Object jmsObject) {
    // Not an IBM MQ object. That is an expected negative for other JMS providers rather than
    // a failure, so this path stays silent.
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
      // Unexpected, so it is logged, but enrichment stays best-effort and the failure never
      // reaches the instrumented application.
      logger.log(FINE, "Failed to read queue manager id from the JMS object", t);
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

  private IbmMqJmsQmid() {}
}
