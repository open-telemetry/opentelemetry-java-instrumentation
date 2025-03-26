/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.nocode;

import java.util.concurrent.atomic.AtomicReference;

public class NocodeEvaluation {

  public interface Evaluator {
    Object evaluate(String expression, Object thiz, Object[] params);

    Object evaluateAtEnd(
        String expression, Object thiz, Object[] params, Object returnValue, Throwable error);
  }

  private static final AtomicReference<Evaluator> globalEvaluator = new AtomicReference<>();

  public static void internalSetEvaluator(Evaluator evaluator) {
    globalEvaluator.set(evaluator);
  }

  public static Object evaluate(String expression, Object thiz, Object[] params) {
    Evaluator e = globalEvaluator.get();
    return e == null ? null : e.evaluate(expression, thiz, params);
  }

  public static Object evaluateAtEnd(
      String expression, Object thiz, Object[] params, Object returnValue, Throwable error) {
    Evaluator e = globalEvaluator.get();
    return e == null ? null : e.evaluateAtEnd(expression, thiz, params, returnValue, error);
  }

  private NocodeEvaluation() {}
}
