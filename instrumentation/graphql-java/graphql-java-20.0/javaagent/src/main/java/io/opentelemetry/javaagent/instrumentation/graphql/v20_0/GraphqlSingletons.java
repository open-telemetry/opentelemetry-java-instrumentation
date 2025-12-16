/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.graphql.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v20_0.GraphQLTelemetry;

public final class GraphqlSingletons {

  private static final GraphQLTelemetry TELEMETRY;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());

    TELEMETRY =
        GraphQLTelemetry.builder(GlobalOpenTelemetry.get())
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
      DeclarativeConfigProperties javaConfig = empty();
      if (openTelemetry instanceof ExtendedOpenTelemetry) {
        ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
        DeclarativeConfigProperties instrumentationConfig =
            extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
        if (instrumentationConfig != null) {
          javaConfig = instrumentationConfig.getStructured("java", empty());
        }
      }
      DeclarativeConfigProperties graphqlConfig = javaConfig.getStructured("graphql", empty());

      this.captureQuery = graphqlConfig.getBoolean("capture_query", true);
      this.sanitizeQuery =
          graphqlConfig.getStructured("query_sanitizer", empty()).getBoolean("enabled", true);
      this.dataFetcherEnabled =
          graphqlConfig.getStructured("data_fetcher", empty()).getBoolean("enabled", false);
      this.trivialDataFetcherEnabled =
          graphqlConfig.getStructured("trivial_data_fetcher", empty()).getBoolean("enabled", false);
      this.addOperationNameToSpanName =
          graphqlConfig
              .getStructured("add_operation_name_to_span_name", empty())
              .getBoolean("enabled", false);
    }
  }

  private GraphqlSingletons() {}
}
