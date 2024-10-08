/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v12_0;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;

@SuppressWarnings({"AbbreviationAsWordInName", "MemberName"})
public final class GraphQLTelemetry {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-12.0";

  /** Returns a new {@link GraphQLTelemetry} configured with the given {@link OpenTelemetry}. */
  public static GraphQLTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link GraphQLTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static GraphQLTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new GraphQLTelemetryBuilder(openTelemetry);
  }

  private final OpenTelemetryInstrumentationHelper helper;

  GraphQLTelemetry(OpenTelemetry openTelemetry, boolean sanitizeQuery) {
    helper =
        OpenTelemetryInstrumentationHelper.create(
            openTelemetry, INSTRUMENTATION_NAME, sanitizeQuery);
  }

  /**
   * Returns a new {@link Instrumentation} that generates telemetry for received GraphQL requests.
   */
  public Instrumentation newInstrumentation() {
    return new OpenTelemetryInstrumentation(helper);
  }
}
