/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v12_0;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.graphql.common.v12_0.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v12_0.GraphQLTelemetry;
import java.util.logging.Logger;

public class GraphqlSingletons {

  private static final Logger logger = Logger.getLogger(GraphqlSingletons.class.getName());

  private static final GraphQLTelemetry telemetry;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    Configuration config = new Configuration(openTelemetry);

    telemetry =
        GraphQLTelemetry.builder(openTelemetry)
            .setCaptureQuery(config.captureQuery)
            .setQuerySanitizationEnabled(config.querySanitizationEnabled)
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
  //       operation_name_in_span_name:
  //         enabled: false
  private static final class Configuration {

    private final boolean captureQuery;
    private final boolean querySanitizationEnabled;
    private final boolean operationNameInSpanNameEnabled;

    Configuration(OpenTelemetry openTelemetry) {
      DeclarativeConfigProperties config =
          DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "graphql");

      this.captureQuery = config.getBoolean("capture_query", true);
      this.querySanitizationEnabled = getQuerySanitizationEnabled(config);
      Boolean deprecatedAddOperationNameToSpanName =
          config.get("add_operation_name_to_span_name").getBoolean("enabled");
      if (deprecatedAddOperationNameToSpanName != null) {
        // Support the deprecated config key until 3.0.
        logger.warning(
            "The otel.instrumentation.graphql.add-operation-name-to-span-name.enabled setting is"
                + " deprecated and will be removed in 3.0. Use "
                + "otel.instrumentation.graphql.operation-name-in-span-name.enabled instead.");
      }
      this.operationNameInSpanNameEnabled =
          config
              .get("operation_name_in_span_name")
              .getBoolean(
                  "enabled",
                  deprecatedAddOperationNameToSpanName != null
                      ? deprecatedAddOperationNameToSpanName
                      : false);
    }

    private static boolean getQuerySanitizationEnabled(DeclarativeConfigProperties config) {
      Boolean querySanitizationEnabled = config.get("query_sanitization").getBoolean("enabled");
      if (querySanitizationEnabled != null) {
        return querySanitizationEnabled;
      }

      Boolean deprecatedQuerySanitizationEnabled =
          config.get("query_sanitizer").getBoolean("enabled");
      if (deprecatedQuerySanitizationEnabled != null) {
        logger.warning(
            "The otel.instrumentation.graphql.query-sanitizer.enabled setting or equivalent"
                + " declarative configuration is deprecated and will be"
                + " removed in 3.0. Use "
                + "otel.instrumentation.graphql.query-sanitization.enabled"
                + " or equivalent declarative configuration instead.");
        return deprecatedQuerySanitizationEnabled;
      }

      return true;
    }
  }

  private GraphqlSingletons() {}
}
