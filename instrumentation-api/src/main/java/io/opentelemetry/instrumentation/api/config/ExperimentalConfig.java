/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

public final class ExperimentalConfig {

  public static boolean suppressControllerSpans() {
    return Config.get()
        .getBoolean("otel.instrumentation.common.experimental.suppress-controller-spans", false);
  }

  public static boolean suppressViewSpans() {
    return Config.get()
        .getBoolean("otel.instrumentation.common.experimental.suppress-view-spans", false);
  }

  private ExperimentalConfig() {}
}
