/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Bridges application {@code Value} objects to agent {@code Value} objects.
 *
 * <p>This class loads the versioned implementation via reflection to handle VALUE type attributes
 * introduced in SDK 1.59.0.
 */
public final class ValueBridging {

  private static final Function<Object, Object> BRIDGE = load();

  private ValueBridging() {}

  @Nullable
  public static Object toAgent(@Nullable Object applicationValue) {
    return BRIDGE.apply(applicationValue);
  }

  // Unchecked cast is safe because ValueBridging159.INSTANCE is known to be Function<Object,
  // Object>
  @SuppressWarnings("unchecked")
  private static Function<Object, Object> load() {
    try {
      Class<?> clazz =
          Class.forName(
              "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_59.ValueBridging159");
      return (Function<Object, Object>) clazz.getField("INSTANCE").get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return v -> null;
    }
  }
}
