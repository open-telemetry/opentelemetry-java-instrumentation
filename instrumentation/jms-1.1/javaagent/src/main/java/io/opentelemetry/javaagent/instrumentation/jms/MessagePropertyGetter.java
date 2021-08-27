/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import javax.jms.JMSException;

public final class MessagePropertyGetter implements TextMapGetter<MessageWithDestination> {

  @Override
  public Iterable<String> keys(MessageWithDestination message) {
    try {
      return Collections.list(message.message().getPropertyNames());
    } catch (JMSException e) {
      return Collections.emptyList();
    }
  }

  @Override
  public String get(MessageWithDestination carrier, String key) {
    String propName = key.replace("-", MessagePropertySetter.DASH);
    final Object value;
    try {
      value = carrier.message().getObjectProperty(propName);
    } catch (JMSException e) {
      throw new IllegalStateException(e);
    }
    if (value instanceof String) {
      return (String) value;
    } else {
      return null;
    }
  }
}
