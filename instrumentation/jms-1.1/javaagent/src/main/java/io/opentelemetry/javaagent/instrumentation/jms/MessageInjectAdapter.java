/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.jms.JMSException;
import javax.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageInjectAdapter implements TextMapSetter<Message> {

  private static final Logger log = LoggerFactory.getLogger(MessageInjectAdapter.class);

  public static final MessageInjectAdapter SETTER = new MessageInjectAdapter();

  static final String DASH = "__dash__";

  @Override
  public void set(Message carrier, String key, String value) {
    String propName = key.replace("-", DASH);
    try {
      carrier.setStringProperty(propName, value);
    } catch (JMSException e) {
      if (log.isDebugEnabled()) {
        log.debug("Failure setting jms property: " + propName, e);
      }
    }
  }
}
