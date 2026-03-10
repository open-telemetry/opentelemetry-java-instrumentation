/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public final class SpanKeyBridging {

  private static final Map<
          application.io.opentelemetry.instrumentation.api.internal.SpanKey, SpanKey>
      agentSpanKeys = createMapping();

  private static Map<application.io.opentelemetry.instrumentation.api.internal.SpanKey, SpanKey>
      createMapping() {

    Map<application.io.opentelemetry.instrumentation.api.internal.SpanKey, SpanKey> map =
        new HashMap<>();
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_SERVER,
        SpanKey.KIND_SERVER);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_CLIENT,
        SpanKey.KIND_CLIENT);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_CONSUMER,
        SpanKey.KIND_CONSUMER);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_PRODUCER,
        SpanKey.KIND_PRODUCER);

    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.HTTP_SERVER,
        SpanKey.HTTP_SERVER);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.RPC_SERVER,
        SpanKey.RPC_SERVER);

    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.HTTP_CLIENT,
        SpanKey.HTTP_CLIENT);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.RPC_CLIENT,
        SpanKey.RPC_CLIENT);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.DB_CLIENT,
        SpanKey.DB_CLIENT);

    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.PRODUCER,
        SpanKey.PRODUCER);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.CONSUMER_RECEIVE,
        SpanKey.CONSUMER_RECEIVE);
    map.put(
        application.io.opentelemetry.instrumentation.api.internal.SpanKey.CONSUMER_PROCESS,
        SpanKey.CONSUMER_PROCESS);
    return map;
  }

  @Nullable
  public static SpanKey toAgentOrNull(
      application.io.opentelemetry.instrumentation.api.internal.SpanKey applicationSpanKey) {
    return agentSpanKeys.get(applicationSpanKey);
  }

  private SpanKeyBridging() {}
}
