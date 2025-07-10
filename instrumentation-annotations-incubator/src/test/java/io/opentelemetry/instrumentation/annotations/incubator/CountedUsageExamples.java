/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations.incubator;

public class CountedUsageExamples {

  @Counted("customizedName")
  public void method() {}

  @Counted("methodWithAttributes")
  public void attributes(@Attribute String attribute1, @Attribute("attribute2") long attribute2) {}
}
