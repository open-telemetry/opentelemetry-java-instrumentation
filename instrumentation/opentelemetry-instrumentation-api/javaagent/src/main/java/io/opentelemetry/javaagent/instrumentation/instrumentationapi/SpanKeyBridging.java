/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import application.io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class SpanKeyBridging {

  private static final Map<SpanKey, io.opentelemetry.instrumentation.api.internal.SpanKey>
      agentSpanKeys = createMapping();

  private static Map<SpanKey, io.opentelemetry.instrumentation.api.internal.SpanKey>
      createMapping() {

    Map<SpanKey, io.opentelemetry.instrumentation.api.internal.SpanKey> map = new HashMap<>();
    map.put(SpanKey.KIND_SERVER, io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_SERVER);
    map.put(SpanKey.KIND_CLIENT, io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_CLIENT);
    map.put(
        SpanKey.KIND_CONSUMER, io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_CONSUMER);
    map.put(
        SpanKey.KIND_PRODUCER, io.opentelemetry.instrumentation.api.internal.SpanKey.KIND_PRODUCER);

    map.put(SpanKey.HTTP_SERVER, io.opentelemetry.instrumentation.api.internal.SpanKey.HTTP_SERVER);
    map.put(SpanKey.RPC_SERVER, io.opentelemetry.instrumentation.api.internal.SpanKey.RPC_SERVER);

    map.put(SpanKey.HTTP_CLIENT, io.opentelemetry.instrumentation.api.internal.SpanKey.HTTP_CLIENT);
    map.put(SpanKey.RPC_CLIENT, io.opentelemetry.instrumentation.api.internal.SpanKey.RPC_CLIENT);
    map.put(SpanKey.DB_CLIENT, io.opentelemetry.instrumentation.api.internal.SpanKey.DB_CLIENT);

    map.put(SpanKey.PRODUCER, io.opentelemetry.instrumentation.api.internal.SpanKey.PRODUCER);
    map.put(
        SpanKey.CONSUMER_RECEIVE,
        io.opentelemetry.instrumentation.api.internal.SpanKey.CONSUMER_RECEIVE);
    map.put(
        SpanKey.CONSUMER_PROCESS,
        io.opentelemetry.instrumentation.api.internal.SpanKey.CONSUMER_PROCESS);
    return map;
  }

  @Nullable
  public static io.opentelemetry.instrumentation.api.internal.SpanKey toAgentOrNull(
      SpanKey applicationSpanKey) {
    return agentSpanKeys.get(applicationSpanKey);
  }
}
