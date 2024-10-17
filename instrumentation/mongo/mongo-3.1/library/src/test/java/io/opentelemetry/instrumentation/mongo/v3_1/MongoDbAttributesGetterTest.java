/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import static io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetryBuilder.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MongoDbAttributesGetterTest {

  @Test
  @DisplayName("should sanitize statements to json")
  void shouldSanitizeStatementsToJson() {
    MongoDbAttributesGetter extractor =
        new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

    assertThat(
            sanitizeStatementAcrossVersions(extractor, new BsonDocument("cmd", new BsonInt32(1))))
        .isEqualTo("{\"cmd\": \"?\"}");

    assertThat(
            sanitizeStatementAcrossVersions(
                extractor,
                new BsonDocument("cmd", new BsonInt32(1))
                    .append("sub", new BsonDocument("a", new BsonInt32(1)))))
        .isEqualTo("{\"cmd\": \"?\", \"sub\": {\"a\": \"?\"}}");

    assertThat(
            sanitizeStatementAcrossVersions(
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
            sanitizeStatementAcrossVersions(
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
        sanitizeStatementAcrossVersions(
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
        sanitizeStatementAcrossVersions(
            extractor,
            new BsonDocument("cmd", new BsonString("c"))
                .append("f1", new BsonArray(asList(new BsonString("c1"), new BsonString("c2"))))
                .append("f2", new BsonString("c3")));

    // This can vary because of different whitespace for different MongoDB versions
    assertThat(normalized)
        .isIn("{\"cmd\": \"c\", \"f1\": [\"?\", \"?", "{\"cmd\": \"c\", \"f1\": [\"?\",");
  }

  static String sanitizeStatementAcrossVersions(
      MongoDbAttributesGetter extractor, BsonDocument query) {
    return sanitizeAcrossVersions(extractor.sanitizeStatement(query));
  }

  static String sanitizeAcrossVersions(String json) {
    json = json.replaceAll("\\{ ", "{");
    json = json.replaceAll(" }", "}");
    json = json.replaceAll(" :", ":");
    return json;
  }
}
