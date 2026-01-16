/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import java.util.ArrayList;
import java.util.List;

final class RpcMetricsAdvice {

  // Stable semconv key
  private static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE =
      AttributeKey.stringKey("rpc.response.status_code");

  // copied from RpcIncubatingAttributes
  @Deprecated // use RPC_RESPONSE_STATUS_CODE for stable semconv
  private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
      AttributeKey.longKey("rpc.grpc.status_code");

  private static final List<AttributeKey<?>> RPC_METRICS_ATTRIBUTE_KEYS = buildAttributeKeysList();

  @SuppressWarnings("deprecation") // until old rpc semconv are dropped
  private static List<AttributeKey<?>> buildAttributeKeysList() {
    List<AttributeKey<?>> keys = new ArrayList<>();

    // Add stable or old RPC system key
    if (SemconvStability.emitStableRpcSemconv()) {
      keys.add(RpcCommonAttributesExtractor.RPC_SYSTEM_NAME);
    } else {
      keys.add(RpcCommonAttributesExtractor.RPC_SYSTEM);
    }

    // Add RPC service (old only)
    if (SemconvStability.emitOldRpcSemconv()) {
      keys.add(RpcCommonAttributesExtractor.RPC_SERVICE);
    }

    // Add stable or old RPC method key
    if (SemconvStability.emitStableRpcSemconv()) {
      keys.add(RpcCommonAttributesExtractor.RPC_METHOD_STABLE);
    } else {
      keys.add(RpcCommonAttributesExtractor.RPC_METHOD);
    }

    // Add status code key
    if (SemconvStability.emitStableRpcSemconv()) {
      keys.add(RPC_RESPONSE_STATUS_CODE);
    } else {
      keys.add(RPC_GRPC_STATUS_CODE);
    }

    // Network type only for old semconv
    if (SemconvStability.emitOldRpcSemconv()) {
      keys.add(NetworkAttributes.NETWORK_TYPE);
    }

    // Common attributes
    keys.add(NetworkAttributes.NETWORK_TRANSPORT);
    keys.add(ServerAttributes.SERVER_ADDRESS);
    keys.add(ServerAttributes.SERVER_PORT);

    return keys;
  }

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

  static void applyClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedLongHistogramBuilder) builder).setAttributesAdvice(RPC_METRICS_ATTRIBUTE_KEYS);
  }

  static void applyServerRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedLongHistogramBuilder) builder).setAttributesAdvice(RPC_METRICS_ATTRIBUTE_KEYS);
  }

  private RpcMetricsAdvice() {}
}
