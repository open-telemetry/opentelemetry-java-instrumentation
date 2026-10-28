/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyEnumeration;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceLoaderUtilTest {

  private final ClassLoader originalContextClassLoader =
      Thread.currentThread().getContextClassLoader();

  @AfterEach
  void tearDown() {
    Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    ServiceLoaderUtil.setLoadFunction(null);
  }

  @Test
  void usesDefaultClassLoaderSupplier() {
    Thread.currentThread().setContextClassLoader(Runnable.class.getClassLoader());
    Runnable provider = () -> {};

    assertThat(ServiceLoaderUtil.load(Runnable.class, () -> singletonList(provider)))
        .containsExactly(provider);
  }

  @Test
  void preservesContextClassLoader() {
    TestClassLoader contextClassLoader = new TestClassLoader();
    Thread.currentThread().setContextClassLoader(contextClassLoader);
    Runnable provider = () -> {};

    assertThat(ServiceLoaderUtil.load(Runnable.class, () -> singletonList(provider))).isEmpty();

    assertThat(contextClassLoader.resourcesRequests)
        .containsExactly("META-INF/services/java.lang.Runnable");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void customLoaderTakesPrecedence(boolean sameClassLoader) {
    Thread.currentThread()
        .setContextClassLoader(
            sameClassLoader ? Runnable.class.getClassLoader() : new TestClassLoader());
    Runnable provider = () -> {};
    ServiceLoaderUtil.setLoadFunction(
        clazz -> {
          assertThat(clazz).isEqualTo(Runnable.class);
          return singletonList(provider);
        });

    assertThat(ServiceLoaderUtil.load(Runnable.class, () -> emptyList())).containsExactly(provider);
  }

  @Test
  void restoresDefaultLoader() {
    Thread.currentThread().setContextClassLoader(Runnable.class.getClassLoader());
    ServiceLoaderUtil.setLoadFunction(unused -> emptyList());
    ServiceLoaderUtil.setLoadFunction(null);
    Runnable provider = () -> {};

    assertThat(ServiceLoaderUtil.load(Runnable.class, () -> singletonList(provider)))
        .containsExactly(provider);
  }

  @Test
  void originalLoadMethodUsesContextClassLoader() {
    TestClassLoader contextClassLoader = new TestClassLoader();
    Thread.currentThread().setContextClassLoader(contextClassLoader);

    assertThat(ServiceLoaderUtil.load(Runnable.class)).isEmpty();

    assertThat(contextClassLoader.resourcesRequests)
        .containsExactly("META-INF/services/java.lang.Runnable");
  }

  @Test
  void originalLoadMethodUsesCustomLoader() {
    Runnable provider = () -> {};
    ServiceLoaderUtil.setLoadFunction(unused -> singletonList(provider));

    assertThat(ServiceLoaderUtil.load(Runnable.class)).containsExactly(provider);
  }

  @Test
  void propagatesCustomLoaderFailure() {
    IllegalStateException failure = new IllegalStateException("custom loader failed");
    ServiceLoaderUtil.setLoadFunction(
        unused -> {
          throw failure;
        });

    assertThatThrownBy(() -> ServiceLoaderUtil.load(Runnable.class, () -> emptyList()))
        .isSameAs(failure);
  }

  private static class TestClassLoader extends ClassLoader {
    final List<String> resourcesRequests = new ArrayList<>();

    @Override
    public Enumeration<URL> getResources(String name) {
      resourcesRequests.add(name);
      return emptyEnumeration();
    }
  }
}
