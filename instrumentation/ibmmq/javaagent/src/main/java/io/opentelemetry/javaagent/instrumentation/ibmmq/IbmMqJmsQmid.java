/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.ibm.msg.client.jms.JmsReadablePropertyContext;
import com.ibm.msg.client.wmq.common.CommonConstants;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;

/**
 * Stamps the IBM MQ Queue Manager Identifier (QMID) onto the span that is already current.
 *
 * <p>This is purely additive enrichment: it never creates, ends or otherwise alters a span, and it
 * never propagates a failure into the application. It exists because queue manager <i>names</i> are
 * not globally unique in federated architectures, whereas the QMID is.
 *
 * <p>The value is read from IBM's own resolved-connection property, which the MQ client populates
 * locally during {@code MQCONN}. {@code JmsPropertyContext} extends {@code Map}, so this is a local
 * lookup rather than an MQI round trip — safe to do on the message path.
 */
public final class IbmMqJmsQmid {

  // Registered in the OpenTelemetry semantic conventions messaging registry as opt_in.
  private static final AttributeKey<String> MESSAGING_IBMMQ_QUEUE_MANAGER_ID =
      AttributeKey.stringKey("messaging.ibmmq.queue_manager.id");

  // opt_in attribute, so it is off unless explicitly enabled.
  private static final boolean ENABLED =
      Boolean.getBoolean("otel.instrumentation.ibmmq.experimental-span-attributes");

  /**
   * Reads the QMID from the supplied IBM MQ JMS object (a producer, consumer or session) and adds
   * it to the current span. Silently does nothing if the attribute is disabled, the object is not
   * an IBM MQ JMS object, no QMID is available, or no span is recording.
   */
  public static void stamp(Object jmsObject) {
    if (!ENABLED || !(jmsObject instanceof JmsReadablePropertyContext)) {
      return;
    }
    try {
      Span span = Span.current();
      if (!span.isRecording()) {
        return;
      }
      String qmid =
          ((JmsReadablePropertyContext) jmsObject)
              .getStringProperty(CommonConstants.WMQ_RESOLVED_QUEUE_MANAGER_ID);
      if (qmid == null) {
        return;
      }
      // MQCA_Q_MGR_IDENTIFIER is a fixed 48-byte, space-padded field.
      qmid = qmid.trim();
      if (!qmid.isEmpty()) {
        span.setAttribute(MESSAGING_IBMMQ_QUEUE_MANAGER_ID, qmid);
      }
    } catch (Throwable t) {
      // Enrichment is best-effort and must never affect the instrumented application.
    }
  }

  private IbmMqJmsQmid() {}
}
