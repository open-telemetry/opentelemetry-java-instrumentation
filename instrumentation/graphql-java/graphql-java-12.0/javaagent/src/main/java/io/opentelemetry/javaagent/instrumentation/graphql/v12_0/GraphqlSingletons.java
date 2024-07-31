/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v12_0;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.graphql.internal.InstrumentationUtil;
import io.opentelemetry.instrumentation.graphql.v12_0.GraphQLTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class GraphqlSingletons {

  private static final boolean QUERY_SANITIZATION_ENABLED =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.graphql.query-sanitizer.enabled", true);

  private static final GraphQLTelemetry TELEMETRY =
      GraphQLTelemetry.builder(GlobalOpenTelemetry.get())
          .setSanitizeQuery(QUERY_SANITIZATION_ENABLED)
          .build();

  private GraphqlSingletons() {}

  public static Instrumentation addInstrumentation(Instrumentation instrumentation) {
    Instrumentation ourInstrumentation = TELEMETRY.newInstrumentation();
    return InstrumentationUtil.addInstrumentation(instrumentation, ourInstrumentation);
  }
}
