/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc;

public class JdbcTracing {

  private static boolean traceEnabled = true;

  /**
   * Sets the {@code traceEnabled} property to enable or disable traces.
   *
   * @param traceEnabled The {@code traceEnabled} value.
   */
  public static void setTraceEnabled(boolean traceEnabled) {
    JdbcTracing.traceEnabled = traceEnabled;
  }

  public static boolean isTraceEnabled() {
    return JdbcTracing.traceEnabled;
  }

  /**
   * can be modified by application code
   */
  private static int slowQueryThresholdMs = Integer
      .getInteger("io.opentracing.contrib.jdbc.slowQueryThresholdMs", 0);

  public static int getSlowQueryThresholdMs() {
    return slowQueryThresholdMs;
  }

  public static void setSlowQueryThresholdMs(final int slowQueryThresholdMs) {
    JdbcTracing.slowQueryThresholdMs = slowQueryThresholdMs;
  }

  private static int excludeFastQueryThresholdMs = Integer
      .getInteger("io.opentracing.contrib.jdbc.excludeFastQueryThresholdMs", 0);

  public static int getExcludeFastQueryThresholdMs() {
    return excludeFastQueryThresholdMs;
  }

  public static void setExcludeFastQueryThresholdMs(final int excludeFastQueryThresholdMs) {
    JdbcTracing.excludeFastQueryThresholdMs = excludeFastQueryThresholdMs;
  }

}
