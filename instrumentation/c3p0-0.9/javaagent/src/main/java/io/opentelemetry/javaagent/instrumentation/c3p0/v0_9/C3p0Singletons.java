/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.c3p0.v0_9;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.c3p0.v0_9.C3p0Telemetry;

public class C3p0Singletons {

  private static final C3p0Telemetry telemetry = C3p0Telemetry.create(GlobalOpenTelemetry.get());

  public static C3p0Telemetry telemetry() {
    return telemetry;
  }

  private C3p0Singletons() {}
}
