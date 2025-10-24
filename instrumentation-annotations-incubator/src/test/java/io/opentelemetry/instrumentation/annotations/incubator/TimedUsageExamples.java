/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations.incubator;

public class TimedUsageExamples {

  @Timed(name = "customizedName")
  public void method() {}

  @Timed(name = "methodWithAttributes")
  public void attributes(
      @Attribute String attribute1, @Attribute(name = "attribute2") long attribute2) {}
}
