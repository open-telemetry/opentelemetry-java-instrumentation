/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

public class JavaLambdaMaker {

  @SuppressWarnings("FunctionalExpressionCanBeFolded")
  public static Runnable lambda(Runnable runnable) {
    return runnable::run;
  }
}
