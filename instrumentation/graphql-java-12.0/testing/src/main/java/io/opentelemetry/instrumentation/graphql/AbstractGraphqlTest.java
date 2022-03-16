/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractGraphqlTest {

  private final List<Map<String, String>> books = new ArrayList<>();
  private final List<Map<String, String>> authors = new ArrayList<>();

  private GraphQL graphql;

  protected abstract InstrumentationExtension getTesting();

  protected abstract void configure(GraphQL.Builder builder);

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
      GraphQLSchema graphqlSchema = buildSchema(reader);
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
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("QUERY findBookById")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    AttributeKey.stringKey("graphql.operation.name"),
                                    "findBookById"),
                                equalTo(AttributeKey.stringKey("graphql.operation.type"), "QUERY"),
                                normalizedQueryEqualsTo(
                                    AttributeKey.stringKey("graphql.source"),
                                    "query findBookById { bookById(id: ?) { name } }")),
                    span -> span.hasName("fetchBookById").hasParent(trace.getSpan(0))));
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
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("QUERY")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("graphql.operation.type"), "QUERY"),
                                normalizedQueryEqualsTo(
                                    AttributeKey.stringKey("graphql.source"),
                                    "query { bookById(id: ?) { name } }")),
                    span -> span.hasName("fetchBookById").hasParent(trace.getSpan(0))));
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
                        span.hasName("GraphQL Query")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfying(Attributes::isEmpty)
                            .hasEventsSatisfyingExactly(
                                event ->
                                    event
                                        .hasName("exception")
                                        .hasAttributesSatisfying(
                                            attrs -> {
                                              assertThat(attrs)
                                                  .containsEntry("exception.type", "InvalidSyntax");
                                              String message =
                                                  String.valueOf(
                                                      attrs
                                                          .asMap()
                                                          .get(
                                                              AttributeKey.stringKey(
                                                                  "exception.message")));
                                              assertThat(message).startsWith("Invalid Syntax");
                                            }))));
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
                        span.hasName("GraphQL Query")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfying(Attributes::isEmpty)
                            .hasEventsSatisfyingExactly(
                                event ->
                                    event
                                        .hasName("exception")
                                        .hasAttributesSatisfying(
                                            attrs -> {
                                              assertThat(attrs)
                                                  .containsEntry(
                                                      "exception.type", "ValidationError");
                                              String message =
                                                  String.valueOf(
                                                      attrs
                                                          .asMap()
                                                          .get(
                                                              AttributeKey.stringKey(
                                                                  "exception.message")));
                                              assertThat(message)
                                                  .startsWith(
                                                      "Validation error of type FieldUndefined");
                                            }))));
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
                        span.hasName("MUTATION addNewBook")
                            .hasKind(SpanKind.INTERNAL)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    AttributeKey.stringKey("graphql.operation.name"), "addNewBook"),
                                equalTo(
                                    AttributeKey.stringKey("graphql.operation.type"), "MUTATION"),
                                normalizedQueryEqualsTo(
                                    AttributeKey.stringKey("graphql.source"),
                                    "mutation addNewBook { addBook(id: ?, name: ?, author: ?) { id } }"))));
  }

  private static AttributeAssertion normalizedQueryEqualsTo(
      AttributeKey<String> key, String value) {
    return satisfies(
        key,
        stringAssert ->
            stringAssert.satisfies(
                querySource -> {
                  String normalized = querySource.replaceAll("(?s)\\s+", " ");
                  assertThat(normalized).isEqualTo(value);
                }));
  }
}
