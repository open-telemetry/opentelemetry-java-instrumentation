/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Configures runtime metrics collection.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface RuntimeMetricsProvider {
  int minJavaVersion();

  @Nullable
  AutoCloseable start(
      OpenTelemetry openTelemetry,
      boolean defaultEnabled,
      Function<String, Boolean> isModuleEnabled);
}
