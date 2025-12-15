/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry;

public final class TracingHolder {

  public static final Tracing TRACING;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());
    TRACING =
        LettuceTelemetry.builder(GlobalOpenTelemetry.get())
            .setStatementSanitizationEnabled(config.statementSanitizerEnabled)
            .setEncodingSpanEventsEnabled(config.commandEncodingEventsEnabled)
            .build()
            .newTracing();
  }

  // instrumentation/development:
  //   java:
  //     common:
  //       db:
  //         statement_sanitizer:
  //           enabled: true
  //     lettuce:
  //       command_encoding_events/development:
  //         enabled: false
  private static final class Configuration {

    private final boolean statementSanitizerEnabled;
    private final boolean commandEncodingEventsEnabled;

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
      this.commandEncodingEventsEnabled =
          javaConfig
              .getStructured("lettuce", empty())
              .getStructured("command_encoding_events/development", empty())
              .getBoolean("enabled", false);
    }
  }

  private TracingHolder() {}
}
