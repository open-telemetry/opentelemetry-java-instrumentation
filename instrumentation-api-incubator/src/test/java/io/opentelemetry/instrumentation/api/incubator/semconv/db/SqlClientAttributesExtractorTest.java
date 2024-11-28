/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class SqlClientAttributesExtractorTest {

  static final class TestAttributesGetter
      implements SqlClientAttributesGetter<Map<String, String>, Void> {

    @Override
    public String getRawQueryText(Map<String, String> map) {
      return map.get("db.statement");
    }

    @Override
    public String getDbSystem(Map<String, String> map) {
      return map.get("db.system");
    }

    @Deprecated
    @Override
    public String getUser(Map<String, String> map) {
      return map.get("db.user");
    }

    @Override
    public String getDbNamespace(Map<String, String> map) {
      return map.get("db.name");
    }

    @Deprecated
    @Override
    public String getConnectionString(Map<String, String> map) {
      return map.get("db.connection_string");
    }
  }

  @SuppressWarnings("deprecation") // TODO DbIncubatingAttributes.DB_CONNECTION_STRING deprecation
  @Test
  void shouldExtractAllAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system", "myDb");
    request.put("db.user", "username");
    request.put("db.name", "potatoes");
    request.put("db.connection_string", "mydb:///potatoes");
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
    if (SemconvStability.emitStableDatabaseSemconv() && SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
              entry(DbIncubatingAttributes.DB_USER, "username"),
              entry(DbIncubatingAttributes.DB_NAME, "potatoes"),
              entry(DbIncubatingAttributes.DB_CONNECTION_STRING, "mydb:///potatoes"),
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(DbIncubatingAttributes.DB_SQL_TABLE, "potato"),
              entry(stringKey("db.namespace"), "potatoes"),
              entry(stringKey("db.query.text"), "SELECT * FROM potato WHERE id=?"),
              entry(stringKey("db.operation.name"), "SELECT"),
              entry(stringKey("db.collection.name"), "potato"));
    } else if (SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
              entry(DbIncubatingAttributes.DB_USER, "username"),
              entry(DbIncubatingAttributes.DB_NAME, "potatoes"),
              entry(DbIncubatingAttributes.DB_CONNECTION_STRING, "mydb:///potatoes"),
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(DbIncubatingAttributes.DB_SQL_TABLE, "potato"));
    } else if (SemconvStability.emitStableDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
              entry(stringKey("db.namespace"), "potatoes"),
              entry(stringKey("db.query.text"), "SELECT * FROM potato WHERE id=?"),
              entry(stringKey("db.operation.name"), "SELECT"),
              entry(stringKey("db.collection.name"), "potato"));
    }

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
    if (SemconvStability.emitStableDatabaseSemconv() && SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT *"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(stringKey("db.query.text"), "SELECT *"),
              entry(stringKey("db.operation.name"), "SELECT"));
    } else if (SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT *"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"));
    } else if (SemconvStability.emitStableDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(stringKey("db.query.text"), "SELECT *"),
              entry(stringKey("db.operation.name"), "SELECT"));
    }
  }

  @Test
  @SuppressWarnings("deprecation") // to support old database semantic conventions
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
    if (SemconvStability.emitStableDatabaseSemconv() && SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM table"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(DbIncubatingAttributes.DB_CASSANDRA_TABLE, "table"),
              entry(stringKey("db.query.text"), "SELECT * FROM table"),
              entry(stringKey("db.operation.name"), "SELECT"),
              entry(stringKey("db.collection.name"), "table"));
    } else if (SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM table"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(DbIncubatingAttributes.DB_CASSANDRA_TABLE, "table"));
    } else if (SemconvStability.emitStableDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(stringKey("db.query.text"), "SELECT * FROM table"),
              entry(stringKey("db.operation.name"), "SELECT"),
              entry(stringKey("db.collection.name"), "table"));
    }
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
