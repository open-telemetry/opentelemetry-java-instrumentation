/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.ServerAddress;
import java.util.stream.Stream;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MongoDbAttributesGetterTest {

  @Test
  @DisplayName("should sanitize queries to json")
  void shouldSanitizeQueriesToJson() {
    MongoDbAttributesGetter extractor =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(sanitizeQueryAcrossVersions(extractor, new BsonDocument("cmd", new BsonInt32(1))))
        .isEqualTo("{\"cmd\": \"?\"}");

    assertThat(
            sanitizeQueryAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonInt32(1))
                    .append("sub", new BsonDocument("a", new BsonInt32(1)))))
        .isEqualTo("{\"cmd\": \"?\", \"sub\": {\"a\": \"?\"}}");

    assertThat(
            sanitizeQueryAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonInt32(1))
                    .append("sub", new BsonArray(singletonList(new BsonInt32(1))))))
        .isEqualTo("{\"cmd\": \"?\", \"sub\": [\"?\"]}");
  }

  @Test
  @DisplayName("should only preserve string value if it is the value of the first top-level key")
  void shouldOnlyPreserveStringValueIfItIsTheValueOfTheFirstTopLevelKey() {
    MongoDbAttributesGetter extractor =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(
            sanitizeQueryAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonString("c"))
                    .append("f", new BsonString("c"))
                    .append("sub", new BsonString("c"))))
        .isEqualTo("{\"cmd\": \"c\", \"f\": \"?\", \"sub\": \"?\"}");
  }

  @Test
  @DisplayName("should truncate simple command")
  void shouldTruncateSimpleCommand() {
    MongoDbAttributesGetter extractor = new MongoDbAttributesGetter(true, 20);

    String normalized =
        sanitizeQueryAcrossVersions(
            extractor,
            new BsonDocument("cmd", new BsonString("c"))
                .append("f1", new BsonString("c1"))
                .append("f2", new BsonString("c2")));

    // This can vary because of different whitespace for different MongoDB versions
    assertThat(normalized).isIn("{\"cmd\": \"c\", \"f1\": \"", "{\"cmd\": \"c\", \"f1\" ");
  }

  @Test
  @DisplayName("should truncate array")
  void shouldTruncateArray() {
    MongoDbAttributesGetter extractor = new MongoDbAttributesGetter(true, 27);

    String normalized =
        sanitizeQueryAcrossVersions(
            extractor,
            new BsonDocument("cmd", new BsonString("c"))
                .append("f1", new BsonArray(asList(new BsonString("c1"), new BsonString("c2"))))
                .append("f2", new BsonString("c3")));

    // This can vary because of different whitespace for different MongoDB versions
    assertThat(normalized)
        .isIn("{\"cmd\": \"c\", \"f1\": [\"?\", \"?", "{\"cmd\": \"c\", \"f1\": [\"?\",");
  }

  @ParameterizedTest
  @MethodSource("errorTypes")
  void getErrorTypeReturnsServerCodeOrFallsBack(Throwable error, String expectedErrorType) {
    MongoDbAttributesGetter getter =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(getter.getErrorType(null, null, error)).isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> errorTypes() {
    return Stream.of(
        argumentSet("server error code", new MongoException(11000, "duplicate key"), "11000"),
        argumentSet("client message sentinel (-3)", new MongoException("boom"), null),
        argumentSet(
            "client message-and-cause sentinel (-4)",
            new MongoException("boom", new RuntimeException()),
            null),
        argumentSet(
            "socket exception sentinel (-2)",
            new MongoSocketException("boom", new ServerAddress()),
            null),
        argumentSet("non-mongo exception", new IllegalStateException("boom"), null),
        argumentSet("no error", null, null));
  }

  private static String sanitizeQueryAcrossVersions(
      MongoDbAttributesGetter extractor, BsonDocument query) {
    return sanitizeAcrossVersions(extractor.sanitizeQuery(query));
  }

  private static String sanitizeAcrossVersions(String json) {
    json = json.replaceAll("\\{ ", "{");
    json = json.replaceAll(" }", "}");
    json = json.replaceAll(" :", ":");
    return json;
  }
}
