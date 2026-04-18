/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkTelemetryFactory;

final class AwsSdkSingletons {

  private static final AwsSdkTelemetry telemetry = AwsSdkTelemetryFactory.legacyLibraryTelemetry();

  static AwsSdkTelemetry telemetry() {
    return telemetry;
  }

  private AwsSdkSingletons() {}
}
