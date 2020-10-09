/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jms;

import io.opentelemetry.context.propagation.TextMapPropagator;
import javax.jms.JMSException;
import javax.jms.Message;

public class MessageExtractAdapter implements TextMapPropagator.Getter<Message> {

  public static final MessageExtractAdapter GETTER = new MessageExtractAdapter();

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
