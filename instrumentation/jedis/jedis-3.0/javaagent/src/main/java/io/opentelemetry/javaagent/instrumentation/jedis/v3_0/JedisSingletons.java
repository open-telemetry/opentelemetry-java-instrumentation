/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-3.0";

  private static final Instrumenter<JedisRequest, Void> INSTRUMENTER;

  static {
    JedisDbAttributeGetter dbAttributeGetter = new JedisDbAttributeGetter();
    JedisNetworkAttributeGetter netAttributeGetter = new JedisNetworkAttributeGetter();

    INSTRUMENTER =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributeGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributeGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netAttributeGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netAttributeGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    netAttributeGetter, CommonConfig.get().getPeerServiceResolver()))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JedisSingletons() {}
}
