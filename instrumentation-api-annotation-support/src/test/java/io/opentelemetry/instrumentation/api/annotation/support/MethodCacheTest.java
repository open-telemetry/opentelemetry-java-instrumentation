/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Method;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MethodCacheTest {

  @Mock private Function<Method, String> fn;

  @Test
  public void getItemFromCache() throws Exception {
    Cache<Method, String> cache = new MethodCache<>();
    Method key = TestClass.class.getDeclaredMethod("method");
    String value = "Value";

    cache.put(key, value);

    assertThat(cache.get(key)).isEqualTo("Value");
  }

  @Test
  void getItemFromCacheWithEquivalentMethod() throws Exception {
    Cache<Method, String> cache = new MethodCache<>();
    Method key = TestClass.class.getDeclaredMethod("method");
    String value = "Value";

    cache.put(key, value);

    Method otherKey = TestClass.class.getDeclaredMethod("method");
    assertThat(otherKey).isNotSameAs(key);
    assertThat(cache.get(otherKey)).isEqualTo(value);
  }

  @Test
  void returnNullWhenNotInCache() throws Exception {
    Cache<Method, String> cache = new MethodCache<>();
    Method key = TestClass.class.getDeclaredMethod("method");

    assertThat(cache.get(key)).isNull();
  }

  @Test
  void computesItemIfAbsent() throws Exception {
    Cache<Method, String> cache = new MethodCache<>();
    Method key = TestClass.class.getDeclaredMethod("method");
    String value = "Value";
    when(fn.apply(key)).thenReturn(value);

    assertThat(cache.computeIfAbsent(key, fn)).isEqualTo(value);
    verify(fn).apply(key);

    Method otherKey = TestClass.class.getDeclaredMethod("method");
    assertThat(cache.computeIfAbsent(otherKey, fn)).isEqualTo(value);
    verifyNoMoreInteractions(fn);
  }

  @Test
  void doesNotComputeItemIfPresent() throws Exception {
    Cache<Method, String> cache = new MethodCache<>();
    Method key = TestClass.class.getDeclaredMethod("method");
    String value = "Value";
    cache.put(key, value);

    assertThat(cache.computeIfAbsent(key, fn)).isEqualTo(value);
    verifyNoInteractions(fn);
  }

  static class TestClass {
    public static void method() {}
  }
}
