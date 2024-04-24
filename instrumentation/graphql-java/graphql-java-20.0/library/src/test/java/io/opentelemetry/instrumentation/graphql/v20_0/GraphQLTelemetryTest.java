/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("AbbreviationAsWordInName")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphQLTelemetryTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final List<Map<String, String>> books = new ArrayList<>();

  private final List<Map<String, String>> authors = new ArrayList<>();

  private GraphQLSchema graphQLSchema;

  @BeforeAll
  void setup() throws IOException {
    // Populate Data
    addAuthor("author-1", "John");
    addAuthor("author-2", "Alice");
    addAuthor("author-3", "Bod");
    addBook("book-1", "First Book", "author-1");
    addBook("book-2", "Second Book", "author-2");
    addBook("book-3", "Third Book", "author-3");

    // Build GraphQLSchema
    InputStream inputStream =
        GraphQLTelemetryTest.class.getClassLoader().getResourceAsStream("schema.graphqls");

    try (Reader reader = new InputStreamReader(inputStream, UTF_8)) {

      SchemaParser schemaParser = new SchemaParser();
      TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(reader);

      RuntimeWiring runtimeWiring =
          newRuntimeWiring()
              .type("Query", builder -> builder.dataFetcher("bookById", getBookByIdDataFetcher()))
              .type("Book", builder -> builder.dataFetcher("author", getAuthorDataFetcher()))
              .build();

      SchemaGenerator schemaGenerator = new SchemaGenerator();

      graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
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

  private DataFetcher<Map<String, String>> getBookByIdDataFetcher() {
    return dataFetchingEnvironment -> {
      String bookId = dataFetchingEnvironment.getArgument("id");
      return books.stream().filter(book -> book.get("id").equals(bookId)).findFirst().orElse(null);
    };
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
    // Arrange
    GraphQLTelemetry telemetry = GraphQLTelemetry.builder(testing.getOpenTelemetry()).build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

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
                                "query findBookById { bookById(id: ?) { name } }"))));
  }

  @Test
  void successfulQueryWithoutName() {
    // Arrange
    GraphQLTelemetry telemetry = GraphQLTelemetry.builder(testing.getOpenTelemetry()).build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    ExecutionResult result =
        graphql.execute(
            ""
                + "  query {\n"
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
                    span.hasName("query")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "query"),
                            normalizedQueryEqualsTo(
                                GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                "{ bookById(id: ?) { name } }"))));
  }

  @Test
  void parseError() {
    // Arrange
    GraphQLTelemetry telemetry = GraphQLTelemetry.builder(testing.getOpenTelemetry()).build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    ExecutionResult result = graphql.execute("query foo bar");

    // Assert
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getErrorType().toString()).isEqualTo("InvalidSyntax");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GraphQL Operation")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfying(Attributes::isEmpty)
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE, "InvalidSyntax"),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            message ->
                                                message.startsWithIgnoringCase(
                                                    "Invalid Syntax"))))));
  }

  @Test
  void validationError() {
    // Arrange
    GraphQLTelemetry telemetry = GraphQLTelemetry.builder(testing.getOpenTelemetry()).build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    // spotless wants to move the query on a single line
    // spotless:off
    ExecutionResult result =
        graphql.execute(
            "" + "  query {\n" + "    book(id: \"a\") {\n" + "      name\n" + "    }\n" + "  }");
    // spotless:on

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getErrorType().toString()).isEqualTo("ValidationError");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GraphQL Operation")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfying(Attributes::isEmpty)
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE, "ValidationError"),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            message -> message.startsWith("Validation error"))))));
  }

  @Test
  void successfulMutation() {
    // Arrange
    GraphQLTelemetry telemetry = GraphQLTelemetry.builder(testing.getOpenTelemetry()).build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

    // Act
    ExecutionResult result =
        graphql.execute(
            ""
                + "  mutation addNewBook {\n"
                + "    addBook(id: \"book-4\", name: \"Fourth Book\", author: \"author-1\") {\n"
                + "      id\n"
                + "    }\n"
                + "  }");

    // Assert
    assertThat(result.getErrors()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("mutation addNewBook")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME, "addNewBook"),
                            equalTo(GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "mutation"),
                            normalizedQueryEqualsTo(
                                GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                "mutation addNewBook { addBook(id: ?, name: ?, author: ?) { id } }"))));
  }

  @Test
  void createSpansForDataFetchers() {
    // Arrange
    GraphQLTelemetry telemetry =
        GraphQLTelemetry.builder(testing.getOpenTelemetry())
            .setDataFetcherInstrumentationEnabled(true)
            .build();

    GraphQL graphql =
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

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
                        .hasParent(testing.spans().get(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.stringKey("graphql.field.name"), "bookById"),
                            equalTo(AttributeKey.stringKey("graphql.field.path"), "/bookById"))));
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
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

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
                        .hasParent(testing.spans().get(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.stringKey("graphql.field.name"), "bookById"),
                            equalTo(AttributeKey.stringKey("graphql.field.path"), "/bookById")),
                span ->
                    span.hasName("name")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(testing.spans().get(0))
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
        GraphQL.newGraphQL(graphQLSchema).instrumentation(telemetry.newInstrumentation()).build();

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
                                "query findBookById { bookById(id: ?) { name } }"))));
  }

  private static AttributeAssertion normalizedQueryEqualsTo(
      AttributeKey<String> key, String value) {
    return satisfies(
        key,
        stringAssert ->
            stringAssert.satisfies(
                querySource -> {
                  String normalized = querySource.replaceAll("(?s)\\s+", " ");
                  if (normalized.startsWith("query {")) {
                    normalized = normalized.substring("query ".length());
                  }
                  assertThat(normalized).isEqualTo(value);
                }));
  }
}
