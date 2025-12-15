/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v12_0;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.graphql.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v12_0.GraphQLTelemetry;

public final class GraphqlSingletons {

  private static final boolean CAPTURE_QUERY =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "graphql", "capture_query")
          .orElse(true);
  private static final boolean QUERY_SANITIZATION_ENABLED =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "graphql", "query_sanitizer", "enabled")
          .orElse(true);
  private static final boolean ADD_OPERATION_NAME_TO_SPAN_NAME =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "graphql",
              "add_operation_name_to_span_name",
              "enabled")
          .orElse(false);

  private static final GraphQLTelemetry TELEMETRY =
      GraphQLTelemetry.builder(GlobalOpenTelemetry.get())
          .setCaptureQuery(CAPTURE_QUERY)
          .setSanitizeQuery(QUERY_SANITIZATION_ENABLED)
          .setAddOperationNameToSpanName(ADD_OPERATION_NAME_TO_SPAN_NAME)
          .build();

  private GraphqlSingletons() {}

  public static Instrumentation addInstrumentation(Instrumentation instrumentation) {
    Instrumentation ourInstrumentation = TELEMETRY.newInstrumentation();
    return InstrumentationUtil.addInstrumentation(instrumentation, ourInstrumentation);
  }
}
