/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

final class DebugUtil {

  static boolean isAgentDebugEnabled() {
    String value = System.getProperty("otel.javaagent.debug");
    if (value == null) {
      value = System.getenv("OTEL_JAVAAGENT_DEBUG");
    }
    return Boolean.parseBoolean(value);
  }

  private DebugUtil() {}

  static boolean isThreadPropagationDebugEnabled() {
    String enabled =
        System.getProperty("otel.javaagent.experimental.thread-propagation-debugger.enabled");
    if (enabled == null) {
      enabled = System.getenv("OTEL_JAVAAGENT_EXPERIMENTAL_THREAD_PROPAGATION_DEBUGGER_ENABLED");
    }
    if (enabled != null) {
      return Boolean.parseBoolean(enabled);
    }
    return isAgentDebugEnabled();
  }
}
