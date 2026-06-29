/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecommonspool2.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachecommonspool2.v2_0.CommonsPool2Telemetry;

public class CommonsPool2Singletons {

  private static final CommonsPool2Telemetry telemetry =
      CommonsPool2Telemetry.create(GlobalOpenTelemetry.get());

  public static CommonsPool2Telemetry telemetry() {
    return telemetry;
  }

  private CommonsPool2Singletons() {}
}
