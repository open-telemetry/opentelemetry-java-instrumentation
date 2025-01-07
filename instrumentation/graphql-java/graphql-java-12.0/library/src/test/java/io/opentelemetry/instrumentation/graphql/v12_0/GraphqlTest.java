/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v12_0;

import graphql.GraphQL;
import io.opentelemetry.instrumentation.graphql.AbstractGraphqlTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class GraphqlTest extends AbstractGraphqlTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected void configure(GraphQL.Builder builder) {
    GraphQLTelemetry telemetry = GraphQLTelemetry.builder(testing.getOpenTelemetry()).build();
    builder.instrumentation(telemetry.newInstrumentation());
  }
}
