/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;

import instrumentation.TestHelperClass;
import io.opentelemetry.javaagent.bootstrap.InstrumentationProxy;
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
        .isEqualTo(-4292813100633930936L);
    assertThat(TestClass.class.getDeclaredFields().length).isEqualTo(0);
  }

  @Test
  void testInjectedClassProxyUnwrap() throws Exception {
    TestClass testClass = new TestClass();
    Class<?> helperType = testClass.testHelperClass();
    assertThat(helperType)
        .describedAs("unable to resolve injected class from instrumented class")
        .isNotNull();

    Object instance = helperType.getConstructor().newInstance();
    if (InstrumentationProxy.class.isAssignableFrom(helperType)) {
      // indy advice: must be an indy proxy

      for (Method method : helperType.getMethods()) {
        assertThat(method.getName())
            .describedAs("proxy method must be hidden from reflection")
            .isNotEqualTo("__getIndyProxyDelegate");
      }

      for (Class<?> interfaceType : helperType.getInterfaces()) {
        assertThat(interfaceType)
            .describedAs("indy proxy interface must be hidden from reflection")
            .isNotEqualTo(InstrumentationProxy.class);
      }

      assertThat(instance).isInstanceOf(InstrumentationProxy.class);

      Object proxyDelegate = ((InstrumentationProxy) instance).__getIndyProxyDelegate();
      assertThat(proxyDelegate).isNotInstanceOf(InstrumentationProxy.class);

    } else {
      // inline advice: must be of the expected type
      assertThat(helperType).isEqualTo(TestHelperClass.class);
      assertThat(instance)
          .isInstanceOf(TestHelperClass.class)
          .isNotInstanceOf(InstrumentationProxy.class);
    }
  }
}
