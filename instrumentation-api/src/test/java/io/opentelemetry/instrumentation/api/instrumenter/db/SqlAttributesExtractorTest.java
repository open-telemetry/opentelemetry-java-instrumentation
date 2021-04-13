/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlAttributesExtractorTest {
  AttributeKey<String> dbTableAttribute;
  SqlAttributesExtractor<Map<String, String>> underTest =
      new SqlAttributesExtractor<Map<String, String>>() {

        @Override
        protected AttributeKey<String> dbTableAttribute() {
          return dbTableAttribute;
        }

        @Override
        protected String rawDbStatement(Map<String, String> map) {
          return map.get("db.statement");
        }

        @Override
        protected String dbSystem(Map<String, String> map) {
          return map.get("db.system");
        }

        @Override
        protected String dbUser(Map<String, String> map) {
          return map.get("db.user");
        }

        @Override
        protected String dbName(Map<String, String> map) {
          return map.get("db.name");
        }

        @Override
        protected String dbConnectionString(Map<String, String> map) {
          return map.get("db.connection_string");
        }
      };

  @Test
  void shouldExtractAllAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system", "myDb");
    request.put("db.user", "username");
    request.put("db.name", "potatoes");
    request.put("db.connection_string", "mydb:///potatoes");
    request.put("db.statement", "SELECT * FROM potato WHERE id=12345");

    dbTableAttribute = SemanticAttributes.DB_SQL_TABLE;

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, request, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.DB_SYSTEM, "myDb"),
            entry(SemanticAttributes.DB_USER, "username"),
            entry(SemanticAttributes.DB_NAME, "potatoes"),
            entry(SemanticAttributes.DB_CONNECTION_STRING, "mydb:///potatoes"),
            entry(SemanticAttributes.DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
            entry(SemanticAttributes.DB_OPERATION, "SELECT"),
            entry(SemanticAttributes.DB_SQL_TABLE, "potato"));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotExtractTableIfAttributeIsNotSet() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.statement", "SELECT * FROM potato WHERE id=12345");

    dbTableAttribute = null;

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, request);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
            entry(SemanticAttributes.DB_OPERATION, "SELECT"));
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Collections.emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }
}
