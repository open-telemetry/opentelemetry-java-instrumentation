/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.graphql.AbstractGraphqlTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GraphqlTest extends AbstractGraphqlTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected void configure(GraphQL.Builder builder) {
    GraphQLTelemetry telemetry =
        GraphQLTelemetry.builder(testing.getOpenTelemetry())
            .setDataFetcherInstrumentationEnabled(true)
            .build();
    builder.instrumentation(telemetry.newInstrumentation());
  }

  @Override
  protected boolean hasDataFetcherSpans() {
    return true;
  }

  @Test
  void createSpansForDataFetchers() {
    // Arrange
    GraphQLTelemetry telemetry =
        GraphQLTelemetry.builder(testing.getOpenTelemetry())
            .setDataFetcherInstrumentationEnabled(true)
            .build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphqlSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query findBookById {\n"
                + "    bookById(id: \"book-1\") {\n"
                + "      name\n"
                + "    }\n"
                + "  }");

    // Assert
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("query findBookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME, "findBookById"),
                            equalTo(GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "query"),
                            normalizedQueryEqualsTo(
                                GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                "query findBookById { bookById(id: ?) { name } }")),
                span ->
                    span.hasName("bookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.stringKey("graphql.field.name"), "bookById"),
                            equalTo(AttributeKey.stringKey("graphql.field.path"), "/bookById")),
                span -> span.hasName("fetchBookById").hasParent(trace.getSpan(1))));
  }

  @Test
  void createSpanForTrivialDataFetchers() {
    // Arrange
    GraphQLTelemetry telemetry =
        GraphQLTelemetry.builder(testing.getOpenTelemetry())
            .setDataFetcherInstrumentationEnabled(true)
            .setTrivialDataFetcherInstrumentationEnabled(true)
            .build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphqlSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query findBookById {\n"
                + "    bookById(id: \"book-1\") {\n"
                + "      name\n"
                + "    }\n"
                + "  }");

    // Assert
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("query findBookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME, "findBookById"),
                            equalTo(GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "query"),
                            normalizedQueryEqualsTo(
                                GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                "query findBookById { bookById(id: ?) { name } }")),
                span ->
                    span.hasName("bookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.stringKey("graphql.field.name"), "bookById"),
                            equalTo(AttributeKey.stringKey("graphql.field.path"), "/bookById")),
                span -> span.hasName("fetchBookById").hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("name")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.stringKey("graphql.field.name"), "name"),
                            equalTo(
                                AttributeKey.stringKey("graphql.field.path"), "/bookById/name"))));
  }

  @Test
  void noDataFetcherSpansCreated() {
    // Arrange
    GraphQLTelemetry telemetry =
        GraphQLTelemetry.builder(testing.getOpenTelemetry())
            .setDataFetcherInstrumentationEnabled(false)
            .setTrivialDataFetcherInstrumentationEnabled(true)
            .build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphqlSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query findBookById {\n"
                + "    bookById(id: \"book-1\") {\n"
                + "      name\n"
                + "    }\n"
                + "  }");

    // Assert
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("query findBookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME, "findBookById"),
                            equalTo(GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "query"),
                            normalizedQueryEqualsTo(
                                GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                "query findBookById { bookById(id: ?) { name } }")),
                span -> span.hasName("fetchBookById").hasParent(trace.getSpan(0))));
  }
}
