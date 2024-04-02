/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Arrays;

final class RpcMetricsAdvice {

  static void applyClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            Arrays.asList(
                SemanticAttributes.RPC_SYSTEM,
                SemanticAttributes.RPC_SERVICE,
                SemanticAttributes.RPC_METHOD,
                SemanticAttributes.RPC_GRPC_STATUS_CODE,
                NetworkAttributes.NETWORK_TYPE,
                NetworkAttributes.NETWORK_TRANSPORT,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT));
  }

  static void applyServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            Arrays.asList(
                SemanticAttributes.RPC_SYSTEM,
                SemanticAttributes.RPC_SERVICE,
                SemanticAttributes.RPC_METHOD,
                SemanticAttributes.RPC_GRPC_STATUS_CODE,
                NetworkAttributes.NETWORK_TYPE,
                NetworkAttributes.NETWORK_TRANSPORT,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT));
  }

  private RpcMetricsAdvice() {}
}
