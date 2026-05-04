/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkTelemetryFactory;

public class AwsSdkSingletons {

  private static final AwsSdkTelemetry telemetry = AwsSdkTelemetryFactory.telemetry();

  public static AwsSdkTelemetry telemetry() {
    return telemetry;
  }

  private AwsSdkSingletons() {}
}
