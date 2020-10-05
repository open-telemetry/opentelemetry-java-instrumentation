/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.trace.Span;

public interface FaasSpanSemanticConvention {
  void end();

  Span getSpan();

  /**
   * Sets a value for faas.trigger
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  FaasSpanSemanticConvention setFaasTrigger(String faasTrigger);

  /**
   * Sets a value for faas.execution
   *
   * @param faasExecution The execution id of the current function execution.
   */
  FaasSpanSemanticConvention setFaasExecution(String faasExecution);
}
