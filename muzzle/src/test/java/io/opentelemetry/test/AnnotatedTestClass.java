/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

@AnnotatedTestClass.TestAnnotation
public class AnnotatedTestClass {

  @AnnotatedTestClass.TestAnnotation
  void testMethod() {}

  public @interface TestAnnotation {}
}
