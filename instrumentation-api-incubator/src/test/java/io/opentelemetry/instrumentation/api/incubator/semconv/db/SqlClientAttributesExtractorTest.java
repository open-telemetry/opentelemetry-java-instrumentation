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
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class SqlClientAttributesExtractorTest {

  static final class TestAttributesGetter
      implements SqlClientAttributesGetter<Map<String, String>> {

    @Deprecated
    @Override
    public String getRawStatement(Map<String, String> map) {
      return map.get("db.statement");
    }

    @Override
    public String getRawQueryText(Map<String, String> map) {
      return map.get("db.query.text");
    }

    @Deprecated
    @Override
    public String getSystem(Map<String, String> map) {
      return map.get("db.system");
    }

    @Nullable
    @Override
    public String getDbSystem(Map<String, String> map) {
      return map.get("db.system");
    }

    @Deprecated
    @Override
    public String getUser(Map<String, String> map) {
      return map.get("db.user");
    }

    @Deprecated
    @Nullable
    @Override
    public String getName(Map<String, String> map) {
      return map.get("db.name");
    }

    @Nullable
    @Override
    public String getDbNamespace(Map<String, String> map) {
      return map.get("db.namespace");
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
    if (SemconvStability.emitOldDatabaseSemconv()) {
      request.put("db.user", "username");
      request.put("db.connection_string", "mydb:///potatoes");
      request.put("db.name", "potatoes");
      request.put("db.statement", "SELECT * FROM potato WHERE id=12345");
    } else if (SemconvStability.emitStableDatabaseSemconv()) {
      request.put("db.namespace", "potatoes");
      request.put("db.query.text", "SELECT * FROM potato WHERE id=12345");
    }

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    if (SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
              entry(DbIncubatingAttributes.DB_USER, "username"),
              entry(DbIncubatingAttributes.DB_NAME, "potatoes"),
              entry(DbIncubatingAttributes.DB_CONNECTION_STRING, "mydb:///potatoes"),
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(DbIncubatingAttributes.DB_SQL_TABLE, "potato"));
    } else {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_NAME), "potatoes"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_STATEMENT),
                  "SELECT * FROM potato WHERE id=?"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_OPERATION),
                  "SELECT"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_SQL_TABLE),
                  "potato"));
    }

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotExtractTableIfAttributeIsNotSet() {
    // given
    Map<String, String> request = new HashMap<>();
    if (SemconvStability.emitOldDatabaseSemconv()) {
      request.put("db.statement", "SELECT *");
    } else {
      request.put("db.query.text", "SELECT *");
    }

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, request);

    // then
    if (SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT *"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"));
    } else {
      assertThat(attributes.build())
          .containsOnly(
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_STATEMENT),
                  "SELECT *"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_OPERATION),
                  "SELECT"));
    }
  }

  @Test
  void shouldExtractTableToSpecifiedKey() {
    // given
    Map<String, String> request = new HashMap<>();
    if (SemconvStability.emitOldDatabaseSemconv()) {
      request.put("db.statement", "SELECT * FROM table");
    } else {
      request.put("db.query.text", "SELECT * FROM table");
    }

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        SqlClientAttributesExtractor.<Map<String, String>, Void>builder(new TestAttributesGetter())
            .setTableAttribute(
                SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_CASSANDRA_TABLE))
            .build();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, request);

    // then
    if (SemconvStability.emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DbIncubatingAttributes.DB_STATEMENT, "SELECT * FROM table"),
              entry(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
              entry(DbIncubatingAttributes.DB_CASSANDRA_TABLE, "table"));
    } else {
      assertThat(attributes.build())
          .containsOnly(
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_STATEMENT),
                  "SELECT * FROM table"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_OPERATION),
                  "SELECT"),
              entry(
                  SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_CASSANDRA_TABLE),
                  "table"));
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
