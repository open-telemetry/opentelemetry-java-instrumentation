/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Internal {

  @Nullable private static volatile BiConsumer<JmxTelemetryBuilder, Map<String, Object>> addRule;

  public static void addRule(JmxTelemetryBuilder builder, Map<String, Object> rule) {
    requireNonNull(addRule, "JMX rule callback has not been registered").accept(builder, rule);
  }

  public static void internalSetAddRule(
      BiConsumer<JmxTelemetryBuilder, Map<String, Object>> callback) {
    addRule = callback;
  }

  private Internal() {}
}
