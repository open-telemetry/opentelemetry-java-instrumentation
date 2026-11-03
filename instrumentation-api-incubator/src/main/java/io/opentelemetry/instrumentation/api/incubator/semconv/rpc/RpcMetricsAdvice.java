/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import java.util.List;

final class RpcMetricsAdvice {

  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0, 50.0, 100.0));

  private static final AttributeKey<String> RPC_STATUS_CODE =
      AttributeKey.stringKey("rpc.status_code");

  private static final List<AttributeKey<?>> RPC_METRICS_ATTRIBUTE_KEYS =
      unmodifiableList(
          asList(
              RpcCommonAttributesExtractor.RPC_SYSTEM_NAME,
              RpcCommonAttributesExtractor.RPC_METHOD,
              RPC_STATUS_CODE,
              ERROR_TYPE,
              SERVER_ADDRESS,
              SERVER_PORT));

  static void applyClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(RPC_METRICS_ATTRIBUTE_KEYS);
  }

  static void applyServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(RPC_METRICS_ATTRIBUTE_KEYS);
  }

  private RpcMetricsAdvice() {}
}
