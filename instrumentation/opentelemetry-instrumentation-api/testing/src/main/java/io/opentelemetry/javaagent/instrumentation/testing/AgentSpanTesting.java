/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.testing;

import io.opentelemetry.instrumentation.api.internal.SpanKey;

public class AgentSpanTesting {

  /**
   * Runs the provided {@code runnable} inside the scope of an SERVER span with name {@code
   * spanName}.
   */
  public static void runWithHttpServerSpan(String spanName, Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs the provided {@code runnable} inside the scope of an INTERNAL span with name {@code
   * spanName}. Span is added into context under all possible keys from {@link SpanKey}
   */
  public static void runWithAllSpanKeys(String spanName, Runnable runnable) {
    runnable.run();
  }
}
