/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingStrategy;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ClassLoadingTarget;
import java.io.IOException;
import java.io.InputStream;
import net.bytebuddy.utility.StreamDrainer;
import org.junit.jupiter.api.Test;

public class ClassLoadingTargetUtilTest {

  @Test
  void checkTarget() {
    // isolated by default when not explicitly set
    testStrategy(AClass.class, ClassLoadingTarget.INSTRUMENTATION_ISOLATED);

    // explicitly set at class level
    testExplicitAnnotation(BClass.class, ClassLoadingTarget.INSTRUMENTATION_ISOLATED);
    testExplicitAnnotation(CClass.class, ClassLoadingTarget.INSTRUMENTATION_SHARED);
    testExplicitAnnotation(DClass.class, ClassLoadingTarget.INSTRUMENTATION_TARGET);

    // explicitly set at package level, we test only once as the implementation is same as class
    byte[] packageByteCode =
        getPackageByteCode(
            "io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.targetcl");
    assertThat(ClassLoadingTargetUtil.getTarget(packageByteCode))
        .isEqualTo(ClassLoadingTarget.INSTRUMENTATION_SHARED);
  }

  private void testStrategy(Class<?> type, ClassLoadingTarget expected) {
    byte[] bytecode = getClassByteCode(type);
    assertThat(ClassLoadingTargetUtil.getTarget(bytecode)).isEqualTo(expected);
  }

  private void testExplicitAnnotation(Class<?> type, ClassLoadingTarget expected) {
    ClassLoadingStrategy annotation = type.getAnnotation(ClassLoadingStrategy.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo(expected);
    testStrategy(type, expected);
  }

  private byte[] getClassByteCode(Class<?> type) {
    String classFileName = type.getName().replace('.', '/') + ".class";
    return getByteCode(classFileName);
  }

  private byte[] getByteCode(String resourcePath) {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(input).isNotNull();
      return StreamDrainer.DEFAULT.drain(input);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private byte[] getPackageByteCode(String packageName) {
    String packageFileName = packageName.replace('.', '/') + "/package-info.class";
    return getByteCode(packageFileName);
  }

  private static class AClass {}

  @ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_ISOLATED)
  private static class BClass {}

  @ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_SHARED)
  private static class CClass {}

  @ClassLoadingStrategy(ClassLoadingTarget.INSTRUMENTATION_TARGET)
  private static class DClass {}
}
