/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlClientAttributesExtractorTest {

  static final class TestAttributesGetter
      implements SqlClientAttributesGetter<Map<String, String>> {

    @Override
    public String getRawStatement(Map<String, String> map) {
      return map.get("db.statement");
    }

    @Override
    public String getSystem(Map<String, String> map) {
      return map.get("db.system");
    }

    @Override
    public String getUser(Map<String, String> map) {
      return map.get("db.user");
    }

    @Override
    public String getName(Map<String, String> map) {
      return map.get("db.name");
    }
  }

  @Test
  void shouldExtractAllAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system", "myDb");
    request.put("db.user", "username");
    request.put("db.name", "potatoes");
    request.put("db.statement", "SELECT * FROM potato WHERE id=12345");

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
            entry(DbIncubatingAttributes.DB_USER, "username"),
            entry(DbIncubatingAttributes.DB_NAME, "potatoes"),
            entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
            entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
            entry(DbIncubatingAttributes.DB_SQL_TABLE, "potato"));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotExtractTableIfAttributeIsNotSet() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.statement", "SELECT *");

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, request);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT *"),
            entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"));
  }

  @Test
  void shouldExtractTableToSpecifiedKey() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.statement", "SELECT * FROM table");

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.<Map<String, String>, Void>builder(new TestAttributesGetter())
            .setTableAttribute(DbIncubatingAttributes.DB_CASSANDRA_TABLE)
            .build();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, request);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM table"),
            entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
            entry(DbIncubatingAttributes.DB_CASSANDRA_TABLE, "table"));
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // when
    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), Collections.emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }
}
