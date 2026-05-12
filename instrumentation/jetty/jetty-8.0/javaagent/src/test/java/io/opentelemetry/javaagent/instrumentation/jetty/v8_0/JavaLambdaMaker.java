/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

class JavaLambdaMaker {

  @SuppressWarnings({"FunctionalExpressionCanBeFolded", "UnnecessaryMethodReference"})
  static Runnable lambda(Runnable runnable) {
    return runnable::run;
  }

  private JavaLambdaMaker() {}
}
