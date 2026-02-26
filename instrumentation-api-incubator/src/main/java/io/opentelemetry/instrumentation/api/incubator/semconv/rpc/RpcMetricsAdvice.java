/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
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

  private static final List<AttributeKey<?>> RPC_METRICS_OLD_ATTRIBUTE_KEYS =
      buildAttributeKeysList(false);
  private static final List<AttributeKey<?>> RPC_METRICS_STABLE_ATTRIBUTE_KEYS =
      buildAttributeKeysList(true);

  @SuppressWarnings("deprecation") // until old rpc semconv are dropped
  private static List<AttributeKey<?>> buildAttributeKeysList(boolean stable) {
    List<AttributeKey<?>> keys = new ArrayList<>();

    // Add stable or old RPC system key
    keys.add(
        stable
            ? RpcCommonAttributesExtractor.RPC_SYSTEM_NAME
            : RpcCommonAttributesExtractor.RPC_SYSTEM);

    // Add RPC service (old only)
    if (!stable) {
      keys.add(RpcCommonAttributesExtractor.RPC_SERVICE);
    }

    keys.add(RpcCommonAttributesExtractor.RPC_METHOD);

    if (stable) {
      keys.add(RPC_RESPONSE_STATUS_CODE);
    } else {
      keys.add(RPC_GRPC_STATUS_CODE);
    }

    // Network type only for old semconv
    if (!stable) {
      keys.add(NETWORK_TYPE);
    }

    // Common attributes
    keys.add(NETWORK_TRANSPORT);
    keys.add(SERVER_ADDRESS);
    keys.add(SERVER_PORT);

    return keys;
  }

  private static List<AttributeKey<?>> getAttributeKeys(boolean stable) {
    return stable ? RPC_METRICS_STABLE_ATTRIBUTE_KEYS : RPC_METRICS_OLD_ATTRIBUTE_KEYS;
  }

  static void applyClientDurationAdvice(DoubleHistogramBuilder builder, boolean stable) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(getAttributeKeys(stable));
  }

  static void applyServerDurationAdvice(DoubleHistogramBuilder builder, boolean stable) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedDoubleHistogramBuilder) builder).setAttributesAdvice(getAttributeKeys(stable));
  }

  static void applyOldClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedLongHistogramBuilder) builder).setAttributesAdvice(RPC_METRICS_OLD_ATTRIBUTE_KEYS);
  }

  static void applyOldServerRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    ((ExtendedLongHistogramBuilder) builder).setAttributesAdvice(RPC_METRICS_OLD_ATTRIBUTE_KEYS);
  }

  private RpcMetricsAdvice() {}
}
