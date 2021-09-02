/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

public class AgentSpanTesting {

  /**
   * Runs the provided {@code runnable} inside the scope of an SERVER span with name {@code
   * spanName}.
   */
  public static void runWithServerSpan(String spanName, Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs the provided {@code runnable} inside the scope of an CONSUMER span with name {@code
   * spanName}.
   */
  public static void runWithConsumerSpan(String spanName, Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs the provided {@code runnable} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public static void runWithClientSpan(String spanName, Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs the provided {@code runnable} inside the scope of an INTERNAL span with name {@code
   * spanName}. Span is added into context under all possible keys from {@link
   * io.opentelemetry.instrumentation.api.instrumenter.SpanKey}
   */
  public static void runWithAllSpanKeys(String spanName, Runnable runnable) {
    runnable.run();
  }
}
