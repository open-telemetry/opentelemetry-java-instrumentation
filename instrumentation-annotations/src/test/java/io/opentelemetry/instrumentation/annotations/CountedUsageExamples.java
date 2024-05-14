/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations;

public class CountedUsageExamples {

  @Counted()
  public void method1() {}

  @Counted("customizedName")
  public void method2() {}

  @Counted
  public void attributes(
      @MetricAttribute String attribute1, @MetricAttribute("attribute2") long attribute2) {}
}
