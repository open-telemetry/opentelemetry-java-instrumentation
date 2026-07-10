/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for enriching the local root span (e.g. the enclosing HTTP server span) with GraphQL
 * telemetry, and for suppressing the GraphQL operation span. These live outside the shared {@code
 * AbstractGraphqlTest} because the behaviour is specific to graphql-java-20.0 and requires an
 * enclosing local root span, established here via {@link
 * InstrumentationExtension#runWithHttpServerSpan}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphqlLocalRootSpanTest {

  private static final String SERVER_SPAN_NAME = "GET";

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final Map<String, String> books = new HashMap<>();

  private GraphQLSchema graphqlSchema;

  @BeforeAll
  void setup() throws IOException {
    books.put("book-1", "First Book");
    try (Reader reader =
        new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("schema.graphqls"), UTF_8)) {
      TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(reader);
      graphqlSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, buildWiring());
    }
  }

  private RuntimeWiring buildWiring() {
    DataFetcher<?> bookById =
        env -> {
          String id = env.getArgument("id");
          if ("book-graphql-error".equals(id)) {
            return DataFetcherResult.newResult()
                .error(GraphqlErrorBuilder.newError(env).message("failed to fetch book").build())
                .build();
          }
          Map<String, String> book = new HashMap<>();
          book.put("id", id);
          book.put("name", books.get(id));
          return book;
        };
    return RuntimeWiring.newRuntimeWiring()
        .type("Query", builder -> builder.dataFetcher("bookById", bookById))
        .build();
  }

  private GraphQL graphql(Consumer<GraphQLTelemetryBuilder> customizer) {
    GraphQLTelemetryBuilder builder = GraphQLTelemetry.builder(testing.getOpenTelemetry());
    customizer.accept(builder);
    return GraphQL.newGraphQL(graphqlSchema)
        .instrumentation(builder.build().createInstrumentation())
        .build();
  }

  private static final String QUERY = "query findBookById { bookById(id: \"book-1\") { name } }";

  private static final String ERROR_QUERY =
      "query findBookById { bookById(id: \"book-graphql-error\") { name } }";

  @Test
  void enrichAndOwnSpan() {
    GraphQL graphql = graphql(b -> b.setAddAttributesToLocalRootSpan(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                        .hasAttribute(GRAPHQL_OPERATION_TYPE, "query"),
                span ->
                    span.hasName("query")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                        .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")));
  }

  @Test
  void enrichWithoutOwnSpan() {
    GraphQL graphql =
        graphql(b -> b.setAddAttributesToLocalRootSpan(true).setOperationSpanEnabled(false));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                        .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")));
    // Exactly one span: no GraphQL operation span was created.
    assertThat(testing.spans()).hasSize(1);
  }

  @Test
  void errorsEnrichedOntoLocalRootStatusNotPromoted() {
    GraphQL graphql = graphql(b -> b.setAddAttributesToLocalRootSpan(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(ERROR_QUERY));
    assertThat(result.getErrors()).isNotEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        // status is NOT promoted to error
                        .hasStatus(StatusData.unset())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, "DataFetchingException"),
                                        equalTo(EXCEPTION_MESSAGE, "failed to fetch book"))),
                span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void promoteErrorStatusWithoutAttributes() {
    GraphQL graphql = graphql(b -> b.setPromoteErrorStatusToLocalRootSpan(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(ERROR_QUERY));
    assertThat(result.getErrors()).isNotEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        // status IS promoted, but no graphql.* attributes are added
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfying(
                            attrs -> {
                              assertThat(attrs.get(GRAPHQL_OPERATION_TYPE)).isNull();
                              assertThat(attrs.get(GRAPHQL_DOCUMENT)).isNull();
                            }),
                span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void promoteErrorStatusWithoutErrorsLeavesStatusUnset() {
    GraphQL graphql = graphql(b -> b.setPromoteErrorStatusToLocalRootSpan(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.unset()),
                span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void defaultsDoNotEnrichLocalRoot() {
    GraphQL graphql = graphql(b -> {});

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfying(
                            attrs -> {
                              assertThat(attrs.get(GRAPHQL_OPERATION_NAME)).isNull();
                              assertThat(attrs.get(GRAPHQL_OPERATION_TYPE)).isNull();
                              assertThat(attrs.get(GRAPHQL_DOCUMENT)).isNull();
                            }),
                span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void captureQueryFalseOmitsDocumentOnLocalRoot() {
    GraphQL graphql = graphql(b -> b.setAddAttributesToLocalRootSpan(true).setCaptureQuery(false));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SERVER_SPAN_NAME)
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                        .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")
                        .hasAttributesSatisfying(
                            attrs -> assertThat(attrs.get(GRAPHQL_DOCUMENT)).isNull()),
                span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }
}
