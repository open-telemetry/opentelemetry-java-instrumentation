/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedbcp.v2_0.ApacheDbcpTelemetry;
import java.util.concurrent.atomic.AtomicInteger;

public class ApacheDbcpSingletons {

  private static final ApacheDbcpTelemetry telemetry =
      ApacheDbcpTelemetry.create(GlobalOpenTelemetry.get());
  private static final AtomicInteger idGenerator = new AtomicInteger(1);

  public static ApacheDbcpTelemetry telemetry() {
    return telemetry;
  }

  public static String getDefaultName() {
    return "dbcp2-" + idGenerator.getAndIncrement();
  }

  private ApacheDbcpSingletons() {}
}
