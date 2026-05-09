/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedbcp.v2_0.ApacheDbcpTelemetry;

public class ApacheDbcpSingletons {

  private static final ApacheDbcpTelemetry telemetry =
      ApacheDbcpTelemetry.create(GlobalOpenTelemetry.get());

  public static ApacheDbcpTelemetry telemetry() {
    return telemetry;
  }

  private ApacheDbcpSingletons() {}
}
