/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.enumeration;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
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
  void preservesContextClassLoader() throws IOException {
    ClassLoader contextClassLoader = mock(ClassLoader.class);
    when(contextClassLoader.getResources("META-INF/services/java.lang.Runnable"))
        .thenReturn(enumeration(emptyList()));
    Thread.currentThread().setContextClassLoader(contextClassLoader);
    Runnable provider = () -> {};

    assertThat(ServiceLoaderUtil.load(Runnable.class, () -> singletonList(provider))).isEmpty();

    verify(contextClassLoader).getResources("META-INF/services/java.lang.Runnable");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void customLoaderTakesPrecedence(boolean sameClassLoader) {
    Thread.currentThread()
        .setContextClassLoader(
            sameClassLoader ? Runnable.class.getClassLoader() : mock(ClassLoader.class));
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
  void originalLoadMethodUsesContextClassLoader() throws IOException {
    ClassLoader contextClassLoader = mock(ClassLoader.class);
    when(contextClassLoader.getResources("META-INF/services/java.lang.Runnable"))
        .thenReturn(enumeration(emptyList()));
    Thread.currentThread().setContextClassLoader(contextClassLoader);

    assertThat(ServiceLoaderUtil.load(Runnable.class)).isEmpty();

    verify(contextClassLoader).getResources("META-INF/services/java.lang.Runnable");
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
}
