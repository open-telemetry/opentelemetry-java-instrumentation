/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.logging.Logger;
import javax.annotation.Nullable;

enum MessagePropertyGetter implements TextMapGetter<MessageWithDestination> {
  INSTANCE;

  private static final Logger logger = Logger.getLogger(MessagePropertyGetter.class.getName());

  @Override
  public Iterable<String> keys(MessageWithDestination message) {
    try {
      return message.message().getPropertyNames();
    } catch (Exception e) {
      logger.log(FINE, "Failure getting JMS property names", e);
      return emptyList();
    }
  }

  @Nullable
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
      logger.log(FINE, "Failure getting JMS property: " + propName, e);
      return null;
    }
    if (value instanceof String) {
      return (String) value;
    }
    return null;
  }
}
