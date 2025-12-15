/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.graphql.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v20_0.GraphQLTelemetry;
import javax.annotation.Nullable;

public final class GraphqlSingletons {

  private static final GraphQLTelemetry TELEMETRY;

  static {
    Configuration config = new Configuration(GlobalOpenTelemetry.get());

    TELEMETRY =
        GraphQLTelemetry.builder(GlobalOpenTelemetry.get())
            .setCaptureQuery(config.captureQuery(true))
            .setSanitizeQuery(config.sanitizeQuery(true))
            .setDataFetcherInstrumentationEnabled(config.dataFetcherEnabled(false))
            .setTrivialDataFetcherInstrumentationEnabled(config.trivialDataFetcherEnabled(false))
            .setAddOperationNameToSpanName(config.addOperationNameToSpanName(false))
            .build();
  }

  private GraphqlSingletons() {}

  public static Instrumentation addInstrumentation(Instrumentation instrumentation) {
    Instrumentation ourInstrumentation = TELEMETRY.newInstrumentation();
    return InstrumentationUtil.addInstrumentation(instrumentation, ourInstrumentation);
  }

  // instrumentation/development:
  //   java:
  //     graphql:
  //       capture_query: [boolean]
  //       query_sanitizer:
  //         enabled: [boolean]
  //       data_fetcher:
  //         enabled: [boolean]
  //       trivial_data_fetcher:
  //         enabled: [boolean]
  //       add_operation_name_to_span_name:
  //         enabled: [boolean]
  private static final class Configuration {

    @Nullable private final Boolean captureQuery;
    @Nullable private final Boolean sanitizeQuery;
    @Nullable private final Boolean dataFetcherEnabled;
    @Nullable private final Boolean trivialDataFetcherEnabled;
    @Nullable private final Boolean addOperationNameToSpanName;

    Configuration(OpenTelemetry openTelemetry) {
      DeclarativeConfigProperties graphqlConfig =
          DeclarativeConfigUtil.getStructured(openTelemetry, "java", empty())
              .getStructured("graphql", empty());

      this.captureQuery = graphqlConfig.getBoolean("capture_query");
      this.sanitizeQuery =
          graphqlConfig.getStructured("query_sanitizer", empty()).getBoolean("enabled");
      this.dataFetcherEnabled =
          graphqlConfig.getStructured("data_fetcher", empty()).getBoolean("enabled");
      this.trivialDataFetcherEnabled =
          graphqlConfig.getStructured("trivial_data_fetcher", empty()).getBoolean("enabled");
      this.addOperationNameToSpanName =
          graphqlConfig
              .getStructured("add_operation_name_to_span_name", empty())
              .getBoolean("enabled");
    }

    boolean captureQuery(boolean defaultValue) {
      return captureQuery != null ? captureQuery : defaultValue;
    }

    boolean sanitizeQuery(boolean defaultValue) {
      return sanitizeQuery != null ? sanitizeQuery : defaultValue;
    }

    boolean dataFetcherEnabled(boolean defaultValue) {
      return dataFetcherEnabled != null ? dataFetcherEnabled : defaultValue;
    }

    boolean trivialDataFetcherEnabled(boolean defaultValue) {
      return trivialDataFetcherEnabled != null ? trivialDataFetcherEnabled : defaultValue;
    }

    boolean addOperationNameToSpanName(boolean defaultValue) {
      return addOperationNameToSpanName != null ? addOperationNameToSpanName : defaultValue;
    }
  }
}
