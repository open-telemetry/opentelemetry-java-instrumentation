/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.customchecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

class OtelDeprecatedApiUsageTest {

  @Test
  void positiveCases() {
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "DeprecatedClass.java",
            "package test;",
            "@Deprecated",
            "public class DeprecatedClass {",
            "  @Deprecated",
            "  public static void deprecatedMethod() {}",
            "  @Deprecated",
            "  public static String DEPRECATED_FIELD = \"old\";",
            "}")
        .addSourceLines(
            "PositiveCases.java",
            "package test;",
            "public class PositiveCases {",
            "  void method() {",
            "    // BUG: Diagnostic contains: Use of deprecated API: deprecatedMethod()",
            "    DeprecatedClass.deprecatedMethod();",
            "    // BUG: Diagnostic contains: Use of deprecated API: DEPRECATED_FIELD",
            "    String s = DeprecatedClass.DEPRECATED_FIELD;",
            "  }",
            "}")
        .doTest();
  }

  @Test
  void positiveCases_classAsFieldType() {
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "DeprecatedClass.java",
            "package test;",
            "@Deprecated",
            "public class DeprecatedClass {}")
        .addSourceLines(
            "PositiveCases.java",
            "package test;",
            "public class PositiveCases {",
            "  // BUG: Diagnostic contains: Use of deprecated API: test.DeprecatedClass",
            "  DeprecatedClass obj;",
            "}")
        .doTest();
  }

  @Test
  void negativeCases_suppressWarningsDeprecation() {
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "DeprecatedClass.java",
            "package test;",
            "@Deprecated",
            "public class DeprecatedClass {",
            "  @Deprecated",
            "  public static void deprecatedMethod() {}",
            "}")
        .addSourceLines(
            "NegativeCases.java",
            "package test;",
            "@SuppressWarnings(\"deprecation\")",
            "public class NegativeCases {",
            "  DeprecatedClass obj;",
            "  void method() {",
            "    DeprecatedClass.deprecatedMethod();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  void negativeCases_suppressWarningsCheckName() {
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "DeprecatedClass.java",
            "package test;",
            "@Deprecated",
            "public class DeprecatedClass {}")
        .addSourceLines(
            "NegativeCases.java",
            "package test;",
            "@SuppressWarnings(\"OtelDeprecatedApiUsage\")",
            "public class NegativeCases {",
            "  DeprecatedClass obj;",
            "}")
        .doTest();
  }

  @Test
  void negativeCases_insideDeprecatedClass() {
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "DeprecatedClass.java",
            "package test;",
            "@Deprecated",
            "public class DeprecatedClass {",
            "  @Deprecated",
            "  public static void deprecatedMethod() {}",
            "}")
        .addSourceLines(
            "NegativeCases.java",
            "package test;",
            "@Deprecated",
            "public class NegativeCases {",
            "  // Inside a deprecated class, using other deprecated APIs is fine",
            "  DeprecatedClass obj;",
            "  void method() {",
            "    DeprecatedClass.deprecatedMethod();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  void negativeCases_sameClassDeprecation() {
    // Matches javac behavior: using deprecated members within the same class doesn't warn
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "SameClass.java",
            "package test;",
            "public class SameClass {",
            "  @Deprecated",
            "  public SameClass() {}",
            "  public static SameClass create() {",
            "    // No warning: deprecated constructor is in same class",
            "    return new SameClass();",
            "  }",
            "  @Deprecated",
            "  public static void deprecatedMethod() {}",
            "  public void caller() {",
            "    // No warning: deprecated method is in same class",
            "    deprecatedMethod();",
            "  }",
            "}")
        .doTest();
  }

  @Test
  void positiveCases_externalDeprecatedApi() {
    // Verify the check detects deprecated APIs from external code (JDK in this case)
    CompilationTestHelper.newInstance(
            OtelDeprecatedApiUsage.class, OtelDeprecatedApiUsageTest.class)
        .addSourceLines(
            "ExternalDeprecated.java",
            "package test;",
            "public class ExternalDeprecated {",
            "  void method(Thread t) {",
            "    // BUG: Diagnostic contains: Use of deprecated API",
            "    t.stop();",
            "  }",
            "}")
        .doTest();
  }
}
