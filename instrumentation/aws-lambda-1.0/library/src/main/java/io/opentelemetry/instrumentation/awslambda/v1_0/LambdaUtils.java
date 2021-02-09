/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.concurrent.TimeUnit;

final class LambdaUtils {

  static void forceFlush() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    if (openTelemetry instanceof OpenTelemetrySdk) {
      ((OpenTelemetrySdk) openTelemetry)
          .getSdkTracerProvider()
          .forceFlush()
          .join(1, TimeUnit.SECONDS);
    }
  }

  private LambdaUtils() {}
}
