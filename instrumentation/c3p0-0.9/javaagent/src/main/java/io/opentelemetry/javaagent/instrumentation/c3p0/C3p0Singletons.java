/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.c3p0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.c3p0.C3p0Telemetry;

public final class C3p0Singletons {

  private static final C3p0Telemetry c3p0Telemetry =
      C3p0Telemetry.create(GlobalOpenTelemetry.get());

  public static C3p0Telemetry telemetry() {
    return c3p0Telemetry;
  }

  private C3p0Singletons() {}
}
