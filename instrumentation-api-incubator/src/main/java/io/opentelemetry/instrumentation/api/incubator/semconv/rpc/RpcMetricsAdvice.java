/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
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
                RpcIncubatingAttributes.RPC_SYSTEM,
                RpcIncubatingAttributes.RPC_SERVICE,
                RpcIncubatingAttributes.RPC_METHOD,
                RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE,
                NetworkAttributes.NETWORK_TYPE,
                NetworkAttributes.NETWORK_TRANSPORT,
                ServerAttributes.SERVER_ADDRESS,
                ServerAttributes.SERVER_PORT));
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
                RpcIncubatingAttributes.RPC_SYSTEM,
                RpcIncubatingAttributes.RPC_SERVICE,
                RpcIncubatingAttributes.RPC_METHOD,
                RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE,
                NetworkAttributes.NETWORK_TYPE,
                NetworkAttributes.NETWORK_TRANSPORT,
                ServerAttributes.SERVER_ADDRESS,
                ServerAttributes.SERVER_PORT));
  }

  private RpcMetricsAdvice() {}
}
