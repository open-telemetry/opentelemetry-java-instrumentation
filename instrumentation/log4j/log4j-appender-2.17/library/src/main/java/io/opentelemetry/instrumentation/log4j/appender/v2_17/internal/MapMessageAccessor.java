/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.message.MapMessage;

final class MapMessageAccessor {

  private static final boolean supportsForEach = supportsForEach();

  static void forEach(MapMessage<?, ?> message, BiConsumer<String, Object> action) {
    if (supportsForEach) {
      forEachLog4j29(message, action);
    } else {
      message.getData().forEach(action);
    }
  }

  private static boolean supportsForEach() {
    try {
      Class<?> consumerClass =
          Class.forName(
              "org.apache.logging.log4j.util.BiConsumer", false, MapMessage.class.getClassLoader());
      MethodHandles.publicLookup()
          .findVirtual(
              MapMessage.class, "forEach", MethodType.methodType(void.class, consumerClass));
      return true;
    } catch (ReflectiveOperationException ignored) {
      // The javaagent also supports Log4j 2.0-2.8.
      return false;
    }
  }

  @NoMuzzle // Available since Log4j 2.9; guarded by supportsForEach.
  private static void forEachLog4j29(MapMessage<?, ?> message, BiConsumer<String, Object> action) {
    message.forEach(action::accept);
  }

  private MapMessageAccessor() {}
}
