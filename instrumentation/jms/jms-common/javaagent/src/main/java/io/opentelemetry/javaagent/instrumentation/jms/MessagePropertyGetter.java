/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

final class MessagePropertyGetter implements TextMapGetter<MessageWithDestination> {

  @Override
  public Iterable<String> keys(MessageWithDestination message) {
    try {
      return message.message().getPropertyNames();
    } catch (Exception e) {
      return emptyList();
    }
  }

  @Override
  public String get(@Nullable MessageWithDestination carrier, String key) {
    if (carrier == null) {
      return null;
    }
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
