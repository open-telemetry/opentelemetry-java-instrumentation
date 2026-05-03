/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp.v11_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.viburdbcp.v11_0.ViburTelemetry;

public class ViburSingletons {

  private static final ViburTelemetry telemetry = ViburTelemetry.create(GlobalOpenTelemetry.get());

  public static ViburTelemetry telemetry() {
    return telemetry;
  }

  private ViburSingletons() {}
}
