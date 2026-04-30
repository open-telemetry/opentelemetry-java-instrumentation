/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldInstalledMarker;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ReflectionTest {

  @Test
  void testOurFieldsAndMethodsAreNotVisibleWithReflection() {
    TestClass test = new TestClass();

    assertThat(test.testMethod()).isEqualTo("instrumented");
    assertThat(test.testMethod2()).isEqualTo("instrumented");

    for (Field field : TestClass.class.getDeclaredFields()) {
      assertThat(field.getName()).doesNotStartWith("__opentelemetry");
    }

    for (Method method : TestClass.class.getDeclaredMethods()) {
      assertThat(method.getName()).doesNotStartWith("__opentelemetry");
    }

    // although marker interfaces are removed from getInterfaces() result class is still assignable
    // to them
    assertThat(TestClass.class).isAssignableTo(VirtualFieldInstalledMarker.class);
    assertThat(TestClass.class).isAssignableTo(VirtualFieldAccessorMarker.class);
    assertThat(TestClass.class.getInterfaces()).containsExactly(Runnable.class, Serializable.class);
  }

  @Test
  void testGeneratedSerialVersionUid() {
    // expected value is computed with serialver utility that comes with jdk
    assertThat(ObjectStreamClass.lookup(TestClass.class).getSerialVersionUID())
        .isEqualTo(-1006206785953990857L);
    assertThat(TestClass.class.getDeclaredFields()).isEmpty();
  }
}
