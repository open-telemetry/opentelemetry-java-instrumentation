/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.api.trace.Span;

public interface FaasTimerSemanticConvention {
  void end();

  Span getSpan();

  /**
   * Sets a value for faas.trigger
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  FaasTimerSemanticConvention setFaasTrigger(String faasTrigger);

  /**
   * Sets a value for faas.execution
   *
   * @param faasExecution The execution id of the current function execution.
   */
  FaasTimerSemanticConvention setFaasExecution(String faasExecution);

  /**
   * Sets a value for faas.time
   *
   * @param faasTime A string containing the function invocation time in the [ISO
   *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
   *     [UTC](https://www.w3.org/TR/NOTE-datetime).
   */
  FaasTimerSemanticConvention setFaasTime(String faasTime);

  /**
   * Sets a value for faas.cron
   *
   * @param faasCron A string containing the schedule period as [Cron
   *     Expression](https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm).
   */
  FaasTimerSemanticConvention setFaasCron(String faasCron);
}
