/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

public class TestLambda {

  static Runnable makeRunnable() {
    return () -> {};
  }

  private TestLambda() {}
}
