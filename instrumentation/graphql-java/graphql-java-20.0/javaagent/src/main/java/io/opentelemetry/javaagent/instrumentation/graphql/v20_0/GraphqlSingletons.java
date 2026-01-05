/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.graphql.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v20_0.GraphQLTelemetry;

public final class GraphqlSingletons {

  private static final GraphQLTelemetry TELEMETRY;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    Configuration config = new Configuration(openTelemetry);

    TELEMETRY =
        GraphQLTelemetry.builder(openTelemetry)
            .setCaptureQuery(config.captureQuery)
            .setSanitizeQuery(config.sanitizeQuery)
            .setDataFetcherInstrumentationEnabled(config.dataFetcherEnabled)
            .setTrivialDataFetcherInstrumentationEnabled(config.trivialDataFetcherEnabled)
            .setAddOperationNameToSpanName(config.addOperationNameToSpanName)
            .build();
  }

  public static Instrumentation addInstrumentation(Instrumentation instrumentation) {
    Instrumentation ourInstrumentation = TELEMETRY.newInstrumentation();
    return InstrumentationUtil.addInstrumentation(instrumentation, ourInstrumentation);
  }

  // instrumentation/development:
  //   java:
  //     graphql:
  //       capture_query: true
  //       query_sanitizer:
  //         enabled: true
  //       data_fetcher:
  //         enabled: false
  //       trivial_data_fetcher:
  //         enabled: false
  //       add_operation_name_to_span_name:
  //         enabled: false
  private static final class Configuration {

    private final boolean captureQuery;
    private final boolean sanitizeQuery;
    private final boolean dataFetcherEnabled;
    private final boolean trivialDataFetcherEnabled;
    private final boolean addOperationNameToSpanName;

    Configuration(OpenTelemetry openTelemetry) {
      ExtendedDeclarativeConfigProperties config =
          DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "graphql");

      this.captureQuery = config.getBoolean("capture_query", true);
      this.sanitizeQuery = config.get("query_sanitizer").getBoolean("enabled", true);
      this.dataFetcherEnabled = config.get("data_fetcher").getBoolean("enabled", false);
      this.trivialDataFetcherEnabled =
          config.get("trivial_data_fetcher").getBoolean("enabled", false);
      this.addOperationNameToSpanName =
          config.get("add_operation_name_to_span_name").getBoolean("enabled", false);
    }
  }

  private GraphqlSingletons() {}
}
