/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import graphql.ErrorType;
import graphql.ExecutionInput;
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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.LogRecordDataAssert;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractGraphqlTest {

  private static final boolean DATA_FETCHER_ENABLED =
      Boolean.getBoolean("otel.instrumentation.graphql.data-fetcher.enabled");

  private final List<Map<String, String>> books = new ArrayList<>();
  private final List<Map<String, String>> authors = new ArrayList<>();
  private CompletableFuture<Object> dataFetcherResult;

  protected GraphQL graphql;
  protected GraphQLSchema graphqlSchema;

  protected abstract InstrumentationExtension getTesting();

  protected abstract void configure(GraphQL.Builder builder);

  protected boolean hasDataFetcherSpans() {
    return false;
  }

  private boolean includeDataFetcher() {
    return DATA_FETCHER_ENABLED && hasDataFetcherSpans();
  }

  @BeforeAll
  void setup() throws IOException {
    addAuthor("author-1", "John");
    addAuthor("author-2", "Alice");
    addAuthor("author-3", "Bod");
    addBook("book-1", "First Book", "author-1");
    addBook("book-2", "Second Book", "author-2");
    addBook("book-3", "Third Book", "author-3");

    try (Reader reader =
        new InputStreamReader(
            requireNonNull(getClass().getClassLoader().getResourceAsStream("schema.graphqls")),
            UTF_8)) {
      graphqlSchema = buildSchema(reader);
      GraphQL.Builder graphqlBuilder = GraphQL.newGraphQL(graphqlSchema);
      configure(graphqlBuilder);
      this.graphql = graphqlBuilder.build();
    }
  }

  private void addBook(String id, String name, String authorId) {
    Map<String, String> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("authorId", authorId);
    books.add(map);
  }

  private void addAuthor(String id, String name) {
    Map<String, String> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    authors.add(map);
  }

  private GraphQLSchema buildSchema(Reader reader) {
    TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(reader);
    RuntimeWiring runtimeWiring = buildWiring();
    SchemaGenerator schemaGenerator = new SchemaGenerator();
    return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
  }

  private RuntimeWiring buildWiring() {
    return RuntimeWiring.newRuntimeWiring()
        .type(newTypeWiring("Query").dataFetcher("bookById", getBookByIdDataFetcher()))
        .type(newTypeWiring("Book").dataFetcher("author", getAuthorDataFetcher()))
        .build();
  }

  private DataFetcher<Object> getBookByIdDataFetcher() {
    return dataFetchingEnvironment ->
        getTesting()
            .runWithSpan(
                "fetchBookById",
                () -> {
                  String bookId = dataFetchingEnvironment.getArgument("id");
                  if (bookId.startsWith("book-async-")) {
                    dataFetcherResult = new CompletableFuture<>();
                    return dataFetcherResult;
                  }
                  DataFetcherResult.Builder<Map<String, String>> builder =
                      DataFetcherResult.newResult();
                  if ("book-exception".equals(bookId)) {
                    throw new IllegalStateException("fetching book failed");
                  } else if ("book-graphql-error".equals(bookId)) {
                    return builder
                        .error(
                            GraphqlErrorBuilder.newError(dataFetchingEnvironment)
                                .message("failed to fetch book")
                                .build())
                        .build();
                  }
                  return builder
                      .data(
                          books.stream()
                              .filter(book -> book.get("id").equals(bookId))
                              .findFirst()
                              .orElse(null))
                      .build();
                });
  }

  private DataFetcher<Map<String, String>> getAuthorDataFetcher() {
    return dataFetchingEnvironment -> {
      Map<String, String> book = dataFetchingEnvironment.getSource();
      String authorId = book.get("authorId");
      return authors.stream()
          .filter(author -> author.get("id").equals(authorId))
          .findFirst()
          .orElse(null);
    };
  }

  @Test
  void successfulQuery() {
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query findBookById {\n"
                + "    bookById(id: \"book-1\") {\n"
                + "      name\n"
                + "    }\n"
                + "  }");

    assertThat(result.getErrors()).isEmpty();

    getTesting()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span ->
                      span.hasName("query findBookById")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(GRAPHQL_OPERATION_NAME, "findBookById"),
                              equalTo(GRAPHQL_OPERATION_TYPE, "query"),
                              normalizedQueryEqualsTo(
                                  GRAPHQL_DOCUMENT,
                                  "query findBookById { bookById(id: ?) { name } }")));
              if (includeDataFetcher()) {
                assertions.add(
                    span ->
                        span.hasName("bookById")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .satisfies(hasScopeSchemaUrl(null))
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("graphql.field.path"), "/bookById"),
                                equalTo(stringKey("graphql.field.name"), "bookById")));
              }
              assertions.add(
                  span ->
                      span.hasName("fetchBookById")
                          .hasParent(trace.getSpan(includeDataFetcher() ? 1 : 0)));

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  @Test
  void successfulQueryWithoutName() {
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query {\n"
                + "    bookById(id: \"book-1\") {\n"
                + "      name\n"
                + "    }\n"
                + "  }");

    assertThat(result.getErrors()).isEmpty();

    getTesting()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span ->
                      span.hasName("query")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(GRAPHQL_OPERATION_TYPE, "query"),
                              normalizedQueryEqualsTo(
                                  GRAPHQL_DOCUMENT, "{ bookById(id: ?) { name } }")));
              if (includeDataFetcher()) {
                assertions.add(
                    span ->
                        span.hasName("bookById")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("graphql.field.path"), "/bookById"),
                                equalTo(stringKey("graphql.field.name"), "bookById")));
              }
              assertions.add(
                  span ->
                      span.hasName("fetchBookById")
                          .hasParent(trace.getSpan(includeDataFetcher() ? 1 : 0)));
              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  @Test
  void parseError() {
    ExecutionResult result = graphql.execute("query foo bar");

    assertThat(result.getErrors())
        .singleElement()
        .satisfies(error -> assertThat(error.getErrorType().toString()).isEqualTo("InvalidSyntax"));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName("GraphQL Operation")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasTotalAttributeCount(0)
                          .hasStatus(StatusData.error());
                      assertStructuredError(
                          span, "InvalidSyntax", result.getErrors().get(0).getMessage());
                    }));
    assertStructuredErrorLogs("InvalidSyntax", result.getErrors().get(0).getMessage());
  }

  @Test
  void validationError() {
    // spotless wants to move the query on a single line
    // spotless:off
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query {\n"
                + "    book(id: \"a\") {\n"
                + "      name\n"
                + "    }\n"
                + "  }");
    // spotless:on

    assertThat(result.getErrors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.getErrorType().toString()).isEqualTo("ValidationError"));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName("GraphQL Operation")
                          .hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasTotalAttributeCount(0)
                          .hasStatus(StatusData.error());
                      assertStructuredError(
                          span, "ValidationError", result.getErrors().get(0).getMessage());
                    }));
    assertStructuredErrorLogs("ValidationError", result.getErrors().get(0).getMessage());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dataFetcherException(boolean asynchronous) {
    assertDataFetcherFailure(asynchronous, true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dataFetcherError(boolean asynchronous) {
    assertDataFetcherFailure(asynchronous, false);
  }

  private void assertDataFetcherFailure(boolean asynchronous, boolean exception) {
    String bookId =
        (asynchronous ? "book-async-" : "book-") + (exception ? "exception" : "graphql-error");
    CompletableFuture<ExecutionResult> execution =
        graphql.executeAsync(
            ExecutionInput.newExecutionInput()
                .query(
                    "query findBookById { bookById(id: \""
                        + bookId
                        + "\") { name author { name } } }"));
    IllegalStateException failure = new IllegalStateException("fetching book failed");
    if (asynchronous) {
      // Complete on another thread after the GraphQL invocation has left its scope.
      CompletableFuture.runAsync(
              () -> {
                if (exception) {
                  dataFetcherResult.completeExceptionally(failure);
                } else {
                  dataFetcherResult.complete(
                      DataFetcherResult.newResult()
                          .error(
                              GraphqlErrorBuilder.newError()
                                  .errorType(ErrorType.DataFetchingException)
                                  .message("failed to fetch book")
                                  .build())
                          .build());
                }
              })
          .join();
    }
    ExecutionResult result = execution.join();
    String message =
        exception
            ? "Exception while fetching data (/bookById) : fetching book failed"
            : "failed to fetch book";
    assertThat(result.getErrors())
        .singleElement()
        .satisfies(error -> assertThat(error.getMessage()).isEqualTo(message));

    getTesting()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span -> {
                    span.hasName("query findBookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(GRAPHQL_OPERATION_NAME, "findBookById"),
                            equalTo(GRAPHQL_OPERATION_TYPE, "query"),
                            normalizedQueryEqualsTo(
                                GRAPHQL_DOCUMENT,
                                "query findBookById { bookById(id: ?) { name author { name } } }"));
                    assertStructuredError(span, "DataFetchingException", message);
                  });
              if (includeDataFetcher()) {
                assertions.add(
                    span -> {
                      span.hasName("bookById")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              equalTo(stringKey("graphql.field.name"), "bookById"),
                              equalTo(stringKey("graphql.field.path"), "/bookById"));
                      if (exception) {
                        span.hasException(emitExceptionAsSpanEvents() ? failure : null);
                      } else {
                        assertStructuredError(span, "DataFetchingException", message);
                      }
                    });
              }
              assertions.add(
                  span -> {
                    span.hasName("fetchBookById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(includeDataFetcher() ? 1 : 0))
                        .hasTotalAttributeCount(0);
                    if (exception && !asynchronous) {
                      span.hasStatus(StatusData.error())
                          .hasException(emitExceptionAsSpanEvents() ? failure : null);
                    } else {
                      span.hasStatus(StatusData.unset()).hasEventsSatisfyingExactly();
                    }
                  });
              trace.hasSpansSatisfyingExactly(assertions);
            });
    if (emitExceptionAsLogs()) {
      List<Consumer<LogRecordDataAssert>> assertions = new ArrayList<>();
      if (exception && !asynchronous) {
        assertions.add(throwableLog(spanWithName("fetchBookById"), failure));
      }
      if (includeDataFetcher()) {
        SpanData fetcherSpan = spanWithName("bookById");
        assertions.add(
            exception
                ? throwableLog(fetcherSpan, failure)
                : structuredErrorLog(fetcherSpan, "DataFetchingException", message));
      }
      assertions.add(
          structuredErrorLog(spanWithName("query findBookById"), "DataFetchingException", message));
      getTesting().waitAndAssertLogRecords(assertions);
    } else {
      assertThat(getTesting().logRecords()).isEmpty();
    }
  }

  private static void assertStructuredError(SpanDataAssert span, String type, String message) {
    if (emitExceptionAsSpanEvents()) {
      span.hasEventsSatisfyingExactly(
          event ->
              event
                  .hasName("exception")
                  .hasAttributesSatisfyingExactly(
                      equalTo(EXCEPTION_TYPE, type), equalTo(EXCEPTION_MESSAGE, message)));
    } else {
      span.hasEventsSatisfyingExactly();
    }
  }

  private void assertStructuredErrorLogs(String type, String message) {
    if (emitExceptionAsLogs()) {
      getTesting()
          .waitAndAssertLogRecords(
              structuredErrorLog(spanWithName("GraphQL Operation"), type, message));
    } else {
      assertThat(getTesting().logRecords()).isEmpty();
    }
  }

  private static Consumer<LogRecordDataAssert> structuredErrorLog(
      SpanData span, String type, String message) {
    return log ->
        log.hasEventName("exception")
            .hasSeverity(Severity.WARN)
            .hasSpanContext(span.getSpanContext())
            .satisfies(
                logData ->
                    assertThat(logData.getInstrumentationScopeInfo())
                        .isEqualTo(span.getInstrumentationScopeInfo()))
            .hasAttributesSatisfyingExactly(
                equalTo(EXCEPTION_TYPE, type), equalTo(EXCEPTION_MESSAGE, message));
  }

  private static Consumer<LogRecordDataAssert> throwableLog(SpanData span, Throwable failure) {
    return log ->
        log.hasEventName("exception")
            .hasSeverity(Severity.WARN)
            .hasSpanContext(span.getSpanContext())
            .hasException(failure)
            .hasTotalAttributeCount(3);
  }

  private SpanData spanWithName(String name) {
    return getTesting().spans().stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing span " + name));
  }

  @Test
  void successfulMutation() {
    ExecutionResult result =
        graphql.execute(
            ""
                + "  mutation addNewBook {\n"
                + "    addBook(id: \"book-4\", name: \"Fourth Book\", author: \"author-1\") {\n"
                + "      id\n"
                + "    }\n"
                + "  }");

    assertThat(result.getErrors()).isEmpty();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("mutation addNewBook")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(GRAPHQL_OPERATION_NAME, "addNewBook"),
                                equalTo(GRAPHQL_OPERATION_TYPE, "mutation"),
                                normalizedQueryEqualsTo(
                                    GRAPHQL_DOCUMENT,
                                    "mutation addNewBook { addBook(id: ?, name: ?, author: ?) { id } }"))));
  }

  protected static AttributeAssertion normalizedQueryEqualsTo(
      AttributeKey<String> key, String value) {
    return satisfies(
        key,
        val ->
            val.satisfies(
                v -> {
                  String normalized = normalizeQuery(v);
                  String valueNormalized = normalizeQuery(value);
                  assertThat(normalized).isEqualTo(valueNormalized);
                }));
  }

  private static String normalizeQuery(String query) {
    if (query == null) {
      return null;
    }

    String normalized =
        query.replaceAll("(?s)\\s+", " ").replaceAll("([{:,]) ", "$1").replaceAll(" ([{}])", "$1");
    if (normalized.startsWith("query{")) {
      normalized = normalized.substring("query".length());
    }
    return normalized;
  }
}
