/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

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
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractGraphqlTest {

  private final List<Map<String, String>> books = new ArrayList<>();
  private final List<Map<String, String>> authors = new ArrayList<>();

  protected GraphQL graphql;
  protected GraphQLSchema graphqlSchema;

  protected abstract InstrumentationExtension getTesting();

  protected abstract void configure(GraphQL.Builder builder);

  protected boolean hasDataFetcherSpans() {
    return false;
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
            this.getClass().getClassLoader().getResourceAsStream("schema.graphqls"),
            StandardCharsets.UTF_8)) {
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

  private DataFetcher<Map<String, String>> getBookByIdDataFetcher() {
    return dataFetchingEnvironment ->
        getTesting()
            .runWithSpan(
                "fetchBookById",
                () -> {
                  String bookId = dataFetchingEnvironment.getArgument("id");
                  return books.stream()
                      .filter(book -> book.get("id").equals(bookId))
                      .findFirst()
                      .orElse(null);
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
                          .hasAttributesSatisfyingExactly(
                              equalTo(
                                  GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME,
                                  "findBookById"),
                              equalTo(GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "query"),
                              normalizedQueryEqualsTo(
                                  GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                  "query findBookById { bookById(id: ?) { name } }")));
              if (hasDataFetcherSpans()) {
                assertions.add(
                    span ->
                        span.hasName("bookById")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("graphql.field.path"), "/bookById"),
                                equalTo(AttributeKey.stringKey("graphql.field.name"), "bookById")));
              }
              assertions.add(
                  span ->
                      span.hasName("fetchBookById")
                          .hasParent(trace.getSpan(hasDataFetcherSpans() ? 1 : 0)));

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
                              equalTo(AttributeKey.stringKey("graphql.operation.type"), "query"),
                              normalizedQueryEqualsTo(
                                  AttributeKey.stringKey("graphql.document"),
                                  "{ bookById(id: ?) { name } }")));
              if (hasDataFetcherSpans()) {
                assertions.add(
                    span ->
                        span.hasName("bookById")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("graphql.field.path"), "/bookById"),
                                equalTo(AttributeKey.stringKey("graphql.field.name"), "bookById")));
              }
              assertions.add(
                  span ->
                      span.hasName("fetchBookById")
                          .hasParent(trace.getSpan(hasDataFetcherSpans() ? 1 : 0)));
              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  @Test
  void parseError() {
    ExecutionResult result = graphql.execute("query foo bar");

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getErrorType().toString()).isEqualTo("InvalidSyntax");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GraphQL Operation")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributes(Attributes.empty())
                            .hasStatus(StatusData.error())
                            .hasEventsSatisfyingExactly(
                                event ->
                                    event
                                        .hasName("exception")
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(EXCEPTION_TYPE, "InvalidSyntax"),
                                            satisfies(
                                                EXCEPTION_MESSAGE,
                                                message ->
                                                    message.startsWithIgnoringCase(
                                                        "Invalid Syntax"))))));
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

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getErrorType().toString()).isEqualTo("ValidationError");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GraphQL Operation")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributes(Attributes.empty())
                            .hasStatus(StatusData.error())
                            .hasEventsSatisfyingExactly(
                                event ->
                                    event
                                        .hasName("exception")
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(EXCEPTION_TYPE, "ValidationError"),
                                            satisfies(
                                                EXCEPTION_MESSAGE,
                                                message ->
                                                    message.startsWith("Validation error"))))));
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
                                equalTo(
                                    GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME,
                                    "addNewBook"),
                                equalTo(
                                    GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE, "mutation"),
                                normalizedQueryEqualsTo(
                                    GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
                                    "mutation addNewBook { addBook(id: ?, name: ?, author: ?) { id } }"))));
  }

  protected static AttributeAssertion normalizedQueryEqualsTo(
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
