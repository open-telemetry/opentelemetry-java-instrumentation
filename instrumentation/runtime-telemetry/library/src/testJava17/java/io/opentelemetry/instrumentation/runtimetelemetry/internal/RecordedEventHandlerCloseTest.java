/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordedEventHandlerCloseTest {

  @Test
  void closeObservablesClearsStateEvenWhenAHandleFails() throws Exception {
    ClassLoader callbackClassLoader = new ClassLoader() {};
    WeakReference<ClassLoader> callbackClassLoaderRef = new WeakReference<>(callbackClassLoader);
    List<AutoCloseable> observables = new ArrayList<>();
    observables.add(
        () -> {
          throw new Exception("failure");
        });
    observables.add(captureClassLoader(callbackClassLoader));
    callbackClassLoader = null;

    RecordedEventHandler.closeObservables(observables);

    assertThat(observables).isEmpty();
    GcUtils.awaitGc(callbackClassLoaderRef, Duration.ofSeconds(10));
    assertThat(callbackClassLoaderRef.get()).isNull();
  }

  private static AutoCloseable captureClassLoader(ClassLoader classLoader) {
    return () -> assertThat(classLoader).isNotNull();
  }
}
