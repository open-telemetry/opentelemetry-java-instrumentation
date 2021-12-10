/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

final class LambdaUtils {

  static void forceFlush(OpenTelemetrySdk openTelemetrySdk, long flushTimeout, TimeUnit unit) {
    CompletableResultCode traceFlush = openTelemetrySdk.getSdkTracerProvider().forceFlush();
    final CompletableResultCode metricsFlush = openTelemetrySdk.getSdkMeterProvider().forceFlush();
    CompletableResultCode.ofAll(Arrays.asList(traceFlush, metricsFlush)).join(flushTimeout, unit);
  }

  private LambdaUtils() {}
}
