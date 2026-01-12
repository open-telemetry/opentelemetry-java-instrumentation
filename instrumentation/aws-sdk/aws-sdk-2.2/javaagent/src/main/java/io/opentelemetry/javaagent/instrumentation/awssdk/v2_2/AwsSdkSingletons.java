/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkTelemetryFactory;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = AwsSdkTelemetryFactory.telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private AwsSdkSingletons() {}
}
