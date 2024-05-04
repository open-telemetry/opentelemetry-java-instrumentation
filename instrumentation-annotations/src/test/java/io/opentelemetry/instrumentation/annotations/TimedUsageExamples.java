/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations;

public class TimedUsageExamples {

  @Timed()
  public void method1() {}

  @Timed("customizedName")
  public void method2() {}

  @Timed
  public void attributes(
      @MetricAttribute String attribute1, @MetricAttribute("attribute2") long attribute2) {}
}
