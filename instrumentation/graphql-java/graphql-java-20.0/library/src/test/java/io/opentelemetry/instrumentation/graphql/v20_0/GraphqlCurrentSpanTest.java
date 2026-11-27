/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
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
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for stamping GraphQL telemetry onto the current span (e.g. the enclosing HTTP server span)
 * and for suppressing the GraphQL operation span. These live outside the shared {@code
 * AbstractGraphqlTest} because the behaviour is specific to graphql-java-20.0; the current-span
 * cases establish an enclosing span via {@link InstrumentationExtension#runWithHttpServerSpan}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphqlCurrentSpanTest {

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
  void stampCurrentSpanAndCreateOwnSpan() {
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(SERVER_SPAN_NAME)
                    .hasKind(SpanKind.SERVER)
                    .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                    .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")
                    .hasAttribute(GRAPHQL_DOCUMENT, "query findBookById{bookById(id:?){name}}"),
            span ->
                span.hasName("query")
                    .hasKind(SpanKind.INTERNAL)
                    .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                    .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")));
  }

  @Test
  void stampCurrentSpanWithoutOwnSpan() {
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true).setOperationSpanEnabled(false));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName(SERVER_SPAN_NAME)
                .hasKind(SpanKind.SERVER)
                .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")));
    // Exactly one span: no GraphQL operation span was created.
    assertThat(testing.spans()).hasSize(1);
  }

  @Test
  void createsOwnSpanAsFallbackWhenNoCurrentSpan() {
    // Operation span disabled and current-span stamping requested, but there is no enclosing span:
    // an operation span must be created anyway so telemetry is not lost.
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true).setOperationSpanEnabled(false));

    ExecutionResult result = graphql.execute(QUERY);
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName("query")
                .hasKind(SpanKind.INTERNAL)
                .hasNoParent()
                .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")));
  }

  @Test
  void dataFetcherSpanReparentsToCurrentSpanWhenOperationSpanDisabled() {
    // With the operation span disabled, data fetcher spans must nest directly under the enclosing
    // server span rather than a (missing) GraphQL operation span.
    GraphQL graphql =
        graphql(b -> b.setOperationSpanEnabled(false).setDataFetcherInstrumentationEnabled(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName(SERVER_SPAN_NAME).hasKind(SpanKind.SERVER).hasNoParent(),
            span ->
                span.hasName("bookById")
                    .hasKind(SpanKind.INTERNAL)
                    .hasParent(trace.getSpan(0))
                    .hasAttribute(stringKey("graphql.field.name"), "bookById")));
  }

  @Test
  void errorsPromoteStatusOnCurrentSpan() {
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(ERROR_QUERY));
    assertThat(result.getErrors()).isNotEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(SERVER_SPAN_NAME)
                    .hasKind(SpanKind.SERVER)
                    // status is promoted to error because we stamp onto the current span
                    .hasStatus(StatusData.error())
                    .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById"),
            span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void stampCurrentSpanWithoutOwnSpanWhenResultHasErrors() {
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true).setOperationSpanEnabled(false));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(ERROR_QUERY));
    assertThat(result.getErrors()).isNotEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName(SERVER_SPAN_NAME)
                .hasKind(SpanKind.SERVER)
                .hasAttribute(GRAPHQL_OPERATION_NAME, "findBookById")
                .hasAttribute(GRAPHQL_OPERATION_TYPE, "query")
                .hasAttribute(GRAPHQL_DOCUMENT, "query findBookById{bookById(id:?){name}}")));
    // Exactly one span: no GraphQL operation span was created.
    assertThat(testing.spans()).hasSize(1);
  }

  @Test
  void suppressedExecutionDoesNotEnrichCurrentSpan() {
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true));

    ExecutionResult result =
        testing.runWithHttpServerSpan(() -> {
          AtomicReference<ExecutionResult> executed = new AtomicReference<>();
          InstrumentationUtil.suppressInstrumentation(
              () -> executed.set(graphql.execute(ERROR_QUERY)));
          return executed.get();
        });
    assertThat(result.getErrors()).isNotEmpty();

    // Suppression applies to enrichment as well as to the operation span: the enclosing span is
    // left untouched, even though the result contains errors.
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName(SERVER_SPAN_NAME)
                .hasKind(SpanKind.SERVER)
                .hasStatus(StatusData.unset())
                .hasAttributesSatisfying(attrs -> {
                  assertThat(attrs.get(GRAPHQL_OPERATION_NAME)).isNull();
                  assertThat(attrs.get(GRAPHQL_OPERATION_TYPE)).isNull();
                  assertThat(attrs.get(GRAPHQL_DOCUMENT)).isNull();
                })));
  }

  @Test
  void defaultsDoNotStampCurrentSpan() {
    GraphQL graphql = graphql(b -> {});

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName(SERVER_SPAN_NAME)
                    .hasKind(SpanKind.SERVER)
                    .hasStatus(StatusData.unset())
                    .hasAttributesSatisfying(attrs -> {
                      assertThat(attrs.get(GRAPHQL_OPERATION_NAME)).isNull();
                      assertThat(attrs.get(GRAPHQL_OPERATION_TYPE)).isNull();
                      assertThat(attrs.get(GRAPHQL_DOCUMENT)).isNull();
                    }),
            span -> span.hasName("query").hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void captureQueryFalseOmitsDocumentOnCurrentSpan() {
    GraphQL graphql = graphql(b -> b.setSpanEnrichmentEnabled(true).setCaptureQuery(false));

    ExecutionResult result = testing.runWithHttpServerSpan(() -> graphql.execute(QUERY));
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(trace ->
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
