/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlConfig;
import io.opentelemetry.instrumentation.graphql.common.v12_0.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v20_0.GraphQLTelemetry;

public class GraphqlSingletons {

  private static final GraphQLTelemetry telemetry;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    Configuration config = new Configuration(openTelemetry);

    telemetry =
        GraphQLTelemetry.builder(openTelemetry)
            .setCaptureQuery(config.captureQuery)
            .setQuerySanitizationEnabled(config.querySanitizationEnabled)
            .setDataFetcherInstrumentationEnabled(config.dataFetcherEnabled)
            .setTrivialDataFetcherInstrumentationEnabled(config.trivialDataFetcherEnabled)
            .setOperationNameInSpanNameEnabled(config.operationNameInSpanNameEnabled)
            .build();
  }

  public static Instrumentation addInstrumentation(Instrumentation instrumentation) {
    Instrumentation ourInstrumentation = telemetry.createInstrumentation();
    return InstrumentationUtil.addInstrumentation(instrumentation, ourInstrumentation);
  }

  // instrumentation/development:
  //   java:
  //     graphql:
  //       capture_query: true
  //       query_sanitization:
  //         enabled: true
  //       data_fetcher:
  //         enabled: false
  //       trivial_data_fetcher:
  //         enabled: false
  //       operation_name_in_span_name:
  //         enabled: false
  private static final class Configuration {

    private final boolean captureQuery;
    private final boolean querySanitizationEnabled;
    private final boolean dataFetcherEnabled;
    private final boolean trivialDataFetcherEnabled;
    private final boolean operationNameInSpanNameEnabled;

    Configuration(OpenTelemetry openTelemetry) {
      DeclarativeConfigProperties config =
          DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "graphql");

      this.captureQuery = config.getBoolean("capture_query", true);
      this.querySanitizationEnabled = GraphqlConfig.getQuerySanitizationEnabled(config);
      this.dataFetcherEnabled = config.get("data_fetcher").getBoolean("enabled", false);
      this.trivialDataFetcherEnabled =
          config.get("trivial_data_fetcher").getBoolean("enabled", false);
      this.operationNameInSpanNameEnabled = GraphqlConfig.getOperationNameInSpanNameEnabled(config);
    }
  }

  private GraphqlSingletons() {}
}
