/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.trace.Span;

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
