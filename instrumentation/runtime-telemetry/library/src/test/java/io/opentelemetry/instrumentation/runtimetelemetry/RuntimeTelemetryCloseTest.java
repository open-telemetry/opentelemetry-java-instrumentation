/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import java.lang.ref.WeakReference;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RuntimeTelemetryCloseTest {

  @RegisterExtension LogCapturer logs = LogCapturer.create().captureForType(RuntimeTelemetry.class);

  @Test
  void close_LogsObservableFailures() {
    AutoCloseable observable =
        () -> {
          throw new RuntimeException("boom");
        };

    RuntimeTelemetry runtimeTelemetry = new RuntimeTelemetry(singletonList(observable), null);
    runtimeTelemetry.close();

    logs.assertContains("Error closing runtime telemetry observable");
  }

  @Test
  void closeReleasesObservableStateWhileRuntimeTelemetryRemainsReachable() throws Exception {
    ClassLoader callbackClassLoader = new ClassLoader() {};
    WeakReference<ClassLoader> callbackClassLoaderRef = new WeakReference<>(callbackClassLoader);
    AutoCloseable observable = captureClassLoader(callbackClassLoader);
    RuntimeTelemetry runtimeTelemetry = new RuntimeTelemetry(singletonList(observable), null);
    callbackClassLoader = null;
    observable = null;

    runtimeTelemetry.close();

    GcUtils.awaitGc(callbackClassLoaderRef, Duration.ofSeconds(10));
    assertThat(callbackClassLoaderRef.get()).isNull();
    assertThat(runtimeTelemetry).isNotNull();
  }

  private static AutoCloseable captureClassLoader(ClassLoader classLoader) {
    return () -> assertThat(classLoader).isNotNull();
  }
}
