/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
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

    ArrayList<AutoCloseable> observables = new ArrayList<>();
    observables.add(observable);
    RuntimeTelemetry runtimeTelemetry = new RuntimeTelemetry(observables, null);
    runtimeTelemetry.close();

    logs.assertContains("Error closing runtime telemetry observable");
  }
}
