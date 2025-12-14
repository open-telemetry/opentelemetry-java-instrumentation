/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v12_0;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.graphql.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v12_0.GraphQLTelemetry;

public final class GraphqlSingletons {

  private static final GraphQLTelemetry TELEMETRY;

  static {
    DeclarativeConfigProperties graphqlConfig =
        DeclarativeConfigUtil.getStructured(GlobalOpenTelemetry.get(), "java", empty())
            .getStructured("graphql", empty());

    boolean captureQuery = graphqlConfig.getBoolean("capture_query", true);
    boolean sanitizeQuery =
        graphqlConfig.getStructured("query_sanitizer", empty()).getBoolean("enabled", true);
    boolean addOperationNameToSpanName =
        graphqlConfig
            .getStructured("add_operation_name_to_span_name", empty())
            .getBoolean("enabled", false);

    TELEMETRY =
        GraphQLTelemetry.builder(GlobalOpenTelemetry.get())
            .setCaptureQuery(captureQuery)
            .setSanitizeQuery(sanitizeQuery)
            .setAddOperationNameToSpanName(addOperationNameToSpanName)
            .build();
  }

  private GraphqlSingletons() {}

  public static Instrumentation addInstrumentation(Instrumentation instrumentation) {
    Instrumentation ourInstrumentation = TELEMETRY.newInstrumentation();
    return InstrumentationUtil.addInstrumentation(instrumentation, ourInstrumentation);
  }
}
