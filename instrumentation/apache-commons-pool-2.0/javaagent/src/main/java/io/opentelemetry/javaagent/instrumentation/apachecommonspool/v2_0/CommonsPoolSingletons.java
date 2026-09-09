/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecommonspool.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachecommonspool.v2_0.CommonsPoolTelemetry;

public class CommonsPoolSingletons {

  private static final CommonsPoolTelemetry telemetry =
      CommonsPoolTelemetry.create(GlobalOpenTelemetry.get());

  public static CommonsPoolTelemetry telemetry() {
    return telemetry;
  }

  private CommonsPoolSingletons() {}
}
