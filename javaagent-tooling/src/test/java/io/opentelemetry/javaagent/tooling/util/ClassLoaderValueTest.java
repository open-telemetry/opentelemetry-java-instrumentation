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
    ClassLoaderValue<String> classLoaderValue = new ClassLoaderValue<>();
    classLoaderValue.put(classLoader, "value");
    assertThat(classLoaderValue.get(classLoader)).isEqualTo("value");
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
