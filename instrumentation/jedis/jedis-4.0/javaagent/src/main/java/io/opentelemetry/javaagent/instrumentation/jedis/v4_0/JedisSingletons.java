/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-3.0";

  private static final Instrumenter<JedisRequest, Void> INSTRUMENTER;

  static {
    DbAttributesExtractor<JedisRequest, Void> attributesExtractor =
        new JedisDbAttributesExtractor();
    SpanNameExtractor<JedisRequest> spanName = DbSpanNameExtractor.create(attributesExtractor);
    JedisNetAttributesGetter netAttributesAdapter = new JedisNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesAdapter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesAdapter))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JedisSingletons() {}
}
