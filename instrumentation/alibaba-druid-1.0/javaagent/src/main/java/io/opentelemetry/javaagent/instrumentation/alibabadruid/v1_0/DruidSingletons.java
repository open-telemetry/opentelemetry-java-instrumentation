/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.alibabadruid.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.alibabadruid.v1_0.DruidTelemetry;

public final class DruidSingletons {

  private static final DruidTelemetry druidTelemetry =
      DruidTelemetry.create(GlobalOpenTelemetry.get());

  public static DruidTelemetry telemetry() {
    return druidTelemetry;
  }

  private DruidSingletons() {}
}
