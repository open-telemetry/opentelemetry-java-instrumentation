/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class OtelInternalJavadocTest {

  @Test
  void test() {
    doTest("internal/InternalJavadocPositiveCases.java");
    doTest("internal/InternalJavadocNegativeCases.java");
  }

  private static void doTest(String path) {
    CompilationTestHelper.newInstance(OtelInternalJavadoc.class, OtelInternalJavadocTest.class)
        .addSourceFile(path)
        .doTest();
  }
}
