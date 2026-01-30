/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsBuilderInternal {

  @Nullable private static volatile Consumer<RuntimeMetricsBuilder> captureGcCauseSetter;

  private RuntimeMetricsBuilderInternal() {}

  // this only exists so that the Java agent and Spring Boot starter can maintain
  // backward compatibility until their next major version bump
  public static RuntimeMetricsBuilder captureGcCause(RuntimeMetricsBuilder builder) {
    if (captureGcCauseSetter != null) {
      captureGcCauseSetter.accept(builder);
    }
    return builder;
  }

  public static void internalSetCaptureGcCauseSetter(
      Consumer<RuntimeMetricsBuilder> captureGcCauseSetter) {
    RuntimeMetricsBuilderInternal.captureGcCauseSetter = captureGcCauseSetter;
  }
}
