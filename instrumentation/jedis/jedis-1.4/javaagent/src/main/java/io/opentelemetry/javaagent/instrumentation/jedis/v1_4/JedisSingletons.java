/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;

public final class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-1.4";

  private static final Instrumenter<JedisRequest, Void> INSTRUMENTER;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());

    JedisDbAttributesGetter dbAttributesGetter =
        new JedisDbAttributesGetter(config.statementSanitizerEnabled);
    JedisNetworkAttributesGetter netAttributesGetter = new JedisNetworkAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    netAttributesGetter, PeerServiceResolver.create(GlobalOpenTelemetry.get())))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  // instrumentation/development:
  //   java:
  //     common:
  //       db:
  //         statement_sanitizer:
  //           enabled: true
  private static final class Configuration {

    private final boolean statementSanitizerEnabled;

    Configuration(OpenTelemetry openTelemetry) {
      DeclarativeConfigProperties javaConfig = empty();
      if (openTelemetry instanceof ExtendedOpenTelemetry) {
        ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
        DeclarativeConfigProperties instrumentationConfig =
            extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
        if (instrumentationConfig != null) {
          javaConfig = instrumentationConfig.getStructured("java", empty());
        }
      }

      this.statementSanitizerEnabled =
          javaConfig
              .getStructured("common", empty())
              .getStructured("db", empty())
              .getStructured("statement_sanitizer", empty())
              .getBoolean("enabled", true);
    }
  }

  private JedisSingletons() {}
}
