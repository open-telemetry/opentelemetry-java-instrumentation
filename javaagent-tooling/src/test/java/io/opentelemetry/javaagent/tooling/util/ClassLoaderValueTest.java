/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ClassLoaderValueTest {

  @Test
  void testValue() {
    testClassLoader(this.getClass().getClassLoader());
    testClassLoader(null);
  }

  void testClassLoader(ClassLoader classLoader) {
    ClassLoaderValue<String> value1 = new ClassLoaderValue<>();
    value1.put(classLoader, "value");
    assertThat(value1.get(classLoader)).isEqualTo("value");

    ClassLoaderValue<String> value2 = new ClassLoaderValue<>();
    String value = "value";
    String ret1 = value2.computeIfAbsent(classLoader, () -> value);
    String ret2 =
        value2.computeIfAbsent(
            classLoader,
            () -> {
              throw new IllegalStateException("Shouldn't be invoked");
            });
    assertThat(ret1).isSameAs(value);
    assertThat(ret2).isSameAs(value);
    assertThat(value2.get(classLoader)).isSameAs(value);
  }

  @Test
  void testGc() throws InterruptedException, TimeoutException {
    ClassLoader testClassLoader = new ClassLoader() {};
    ClassLoaderValue<Value> classLoaderValue = new ClassLoaderValue<>();
    Value value = new Value();
    classLoaderValue.put(testClassLoader, value);
    WeakReference<Value> valueWeakReference = new WeakReference<>(value);
    WeakReference<ClassLoader> classLoaderWeakReference = new WeakReference<>(testClassLoader);

    assertThat(classLoaderWeakReference.get()).isNotNull();
    assertThat(valueWeakReference.get()).isNotNull();

    value = null;
    testClassLoader = null;

    GcUtils.awaitGc(classLoaderWeakReference, Duration.ofSeconds(10));
    GcUtils.awaitGc(valueWeakReference, Duration.ofSeconds(10));

    assertThat(classLoaderWeakReference.get()).isNull();
    assertThat(valueWeakReference.get()).isNull();
  }

  private static class Value {}
}
