/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class LettuceSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-5.0";

  private static final Instrumenter<RedisCommand<?, ?, ?>, Void> INSTRUMENTER;
  private static final Instrumenter<RedisURI, Void> CONNECT_INSTRUMENTER;
  private static final Configuration CONFIG;

  public static final ContextKey<Context> COMMAND_CONTEXT_KEY =
      ContextKey.named("opentelemetry-lettuce-v5_0-context-key");

  public static final VirtualField<AsyncCommand<?, ?, ?>, Context> CONTEXT =
      VirtualField.find(AsyncCommand.class, Context.class);

  static {
    CONFIG = new Configuration(GlobalOpenTelemetry.get());

    LettuceDbAttributesGetter dbAttributesGetter =
        new LettuceDbAttributesGetter(CONFIG.statementSanitizerEnabled);

    INSTRUMENTER =
        Instrumenter.<RedisCommand<?, ?, ?>, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    LettuceConnectNetworkAttributesGetter connectNetworkAttributesGetter =
        new LettuceConnectNetworkAttributesGetter();

    CONNECT_INSTRUMENTER =
        Instrumenter.<RedisURI, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, redisUri -> "CONNECT")
            .addAttributesExtractor(
                ServerAttributesExtractor.create(connectNetworkAttributesGetter))
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    connectNetworkAttributesGetter,
                    PeerServiceResolver.create(GlobalOpenTelemetry.get())))
            .addAttributesExtractor(new LettuceConnectAttributesExtractor())
            .setEnabled(CONFIG.connectionTelemetryEnabled)
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static boolean experimentalSpanAttributes() {
    return CONFIG.experimentalSpanAttributes;
  }

  public static Instrumenter<RedisCommand<?, ?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<RedisURI, Void> connectInstrumenter() {
    return CONNECT_INSTRUMENTER;
  }

  // instrumentation/development:
  //   java:
  //     common:
  //       db:
  //         statement_sanitizer:
  //           enabled: true
  //     lettuce:
  //       connection_telemetry:
  //         enabled: false
  //       experimental_span_attributes: false
  private static final class Configuration {

    private final boolean statementSanitizerEnabled;
    private final boolean connectionTelemetryEnabled;
    private final boolean experimentalSpanAttributes;

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
      DeclarativeConfigProperties lettuceConfig = javaConfig.getStructured("lettuce", empty());

      this.statementSanitizerEnabled =
          javaConfig
              .getStructured("common", empty())
              .getStructured("db", empty())
              .getStructured("statement_sanitizer", empty())
              .getBoolean("enabled", true);
      this.connectionTelemetryEnabled =
          lettuceConfig.getStructured("connection_telemetry", empty()).getBoolean("enabled", false);
      this.experimentalSpanAttributes =
          lettuceConfig.getBoolean("experimental_span_attributes", false);
    }
  }

  private LettuceSingletons() {}
}
