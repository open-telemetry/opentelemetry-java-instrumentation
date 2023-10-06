/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.VirtualFieldAccessorMarker;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ReflectionTest {

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
    assertThat(VirtualFieldInstalledMarker.class.isAssignableFrom(TestClass.class)).isTrue();
    assertThat(VirtualFieldAccessorMarker.class.isAssignableFrom(TestClass.class)).isTrue();
    assertThat(TestClass.class.getInterfaces().length).isEqualTo(2);
    assertThat(TestClass.class.getInterfaces()).isInstanceOfAny(Runnable.class, Serializable.class);
  }

  @Test
  void testGeneratedSerialVersionUid() {
    // expected value is computed with serialver utility that comes with jdk
    assertThat(ObjectStreamClass.lookup(TestClass.class).getSerialVersionUID())
        .isEqualTo(-1508684692096503670L);
    assertThat(TestClass.class.getDeclaredFields().length).isEqualTo(0);
  }
}
