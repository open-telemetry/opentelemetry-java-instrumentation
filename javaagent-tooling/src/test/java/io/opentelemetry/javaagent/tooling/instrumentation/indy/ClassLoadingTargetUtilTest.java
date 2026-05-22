/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingStrategy;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingTarget;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.targetcl.DummyInherit;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.targetcl.DummyOverride;
import org.junit.jupiter.api.Test;

public class ClassLoadingTargetUtilTest {

  @Test
  void checkTarget() {
    // not defined at class nor package level
    testStrategy(AClass.class, null);

    // explicitly set at class level
    testExplicitAnnotation(BClass.class, ClassLoadingTarget.INSTRUMENTATION_ISOLATED);
    testExplicitAnnotation(CClass.class, ClassLoadingTarget.INSTRUMENTATION_SHARED);
    testExplicitAnnotation(DClass.class, ClassLoadingTarget.INSTRUMENTATION_TARGET);

    // explicitly set at package level
    String packageName = "io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.targetcl";
    assertThat(ClassLoadingTargetUtil.packageTarget(packageName, getClass().getClassLoader()))
        .isEqualTo(ClassLoadingTarget.INSTRUMENTATION_SHARED);

    // package defined values inherited on class
    assertThat(DummyInherit.class.getAnnotation(ClassLoadingStrategy.class))
        .describedAs("no annotation is present on class")
        .isNull();
    assertThat(
            ClassLoadingTargetUtil.getClassTarget(
                DummyInherit.class.getName(), getClass().getClassLoader()))
        .describedAs("package annotation is applied for class lookup")
        .isEqualTo(ClassLoadingTarget.INSTRUMENTATION_SHARED);

    // class lookup has priority over package when defined on both
    testExplicitAnnotation(DummyOverride.class, ClassLoadingTarget.INSTRUMENTATION_ISOLATED);

    // should defend against non-existing class
    assertThat(
            ClassLoadingTargetUtil.getClassTarget(
                "this.class.does.not.Exists", getClass().getClassLoader()))
        .isNull();
  }

  private void testStrategy(Class<?> type, ClassLoadingTarget expected) {
    assertThat(ClassLoadingTargetUtil.getClassTarget(type.getName(), getClass().getClassLoader()))
        .isEqualTo(expected);
  }

  private void testExplicitAnnotation(Class<?> type, ClassLoadingTarget expected) {
    ClassLoadingStrategy annotation = type.getAnnotation(ClassLoadingStrategy.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo(expected);
    testStrategy(type, expected);
  }

  private static class AClass {}

  @ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_ISOLATED)
  private static class BClass {}

  @ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_SHARED)
  private static class CClass {}

  @ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_TARGET)
  private static class DClass {}
}
