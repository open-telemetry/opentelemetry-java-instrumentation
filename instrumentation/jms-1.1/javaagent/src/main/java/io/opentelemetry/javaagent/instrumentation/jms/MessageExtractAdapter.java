/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import javax.jms.JMSException;
import javax.jms.Message;

public class MessageExtractAdapter implements TextMapGetter<Message> {

  public static final MessageExtractAdapter GETTER = new MessageExtractAdapter();

  @Override
  public Iterable<String> keys(Message message) {
    try {
      return Collections.list(message.getPropertyNames());
    } catch (JMSException e) {
      return Collections.emptyList();
    }
  }

  @Override
  public String get(Message carrier, String key) {
    String propName = key.replace("-", MessageInjectAdapter.DASH);
    Object value;
    try {
      value = carrier.getObjectProperty(propName);
    } catch (JMSException e) {
      throw new RuntimeException(e);
    }
    if (value instanceof String) {
      return (String) value;
    } else {
      return null;
    }
  }
}
