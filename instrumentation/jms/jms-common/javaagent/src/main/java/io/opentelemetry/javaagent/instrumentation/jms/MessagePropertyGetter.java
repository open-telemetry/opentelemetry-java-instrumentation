/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;

enum MessagePropertyGetter implements TextMapGetter<MessageWithDestination> {
  INSTANCE;

  @Override
  public Iterable<String> keys(MessageWithDestination message) {
    try {
      return message.message().getPropertyNames();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public String get(MessageWithDestination carrier, String key) {
    String propName = key.replace("-", MessagePropertySetter.DASH);
    Object value;
    try {
      value = carrier.message().getObjectProperty(propName);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (value instanceof String) {
      return (String) value;
    } else {
      return null;
    }
  }
}
