/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

public class InternalJavadocTest {

  @Test
  public void test() {
    test("internal/InternalJavadocPositiveCases.java");
    test("internal/InternalJavadocNegativeCases.java");
  }

  private static void test(String path) {
    CompilationTestHelper.newInstance(InternalJavadoc.class, InternalJavadocTest.class)
        .addSourceFile(path)
        .doTest();
  }
}
