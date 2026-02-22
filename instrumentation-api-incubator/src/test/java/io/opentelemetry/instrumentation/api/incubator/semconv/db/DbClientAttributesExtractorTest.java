/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class DbClientAttributesExtractorTest {

  static final class TestAttributesGetter
      implements DbClientAttributesGetter<Map<String, String>, Void> {
    @Override
    public String getDbSystemName(Map<String, String> map) {
      return map.get("db.system");
    }

    @Deprecated
    @Override
    public String getUser(Map<String, String> map) {
      return map.get("db.user");
    }

    @Override
    public String getDbNamespace(Map<String, String> map) {
      return map.get("db.namespace");
    }

    @Deprecated
    @Override
    public String getConnectionString(Map<String, String> map) {
      return map.get("db.connection_string");
    }

    @Override
    public String getDbQueryText(Map<String, String> map) {
      return map.get("db.query.text");
    }

    @Nullable
    @Override
    public String getDbQuerySummary(Map<String, String> map) {
      return map.get("db.query_summary");
    }

    @Override
    public String getDbOperationName(Map<String, String> map) {
      return map.get("db.operation.name");
    }
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void shouldExtractAllAvailableAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system", "myDb");
    request.put("db.user", "username");
    request.put("db.namespace", "potatoes");
    request.put("db.connection_string", "mydb:///potatoes");
    request.put("db.query.text", "SELECT * FROM potato");
    request.put("db.query_summary", "SELECT potato");
    request.put("db.operation.name", "SELECT");

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        DbClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_SYSTEM, "myDb"),
              entry(DB_SYSTEM_NAME, "myDb"),
              entry(DB_USER, "username"),
              entry(DB_NAME, "potatoes"),
              entry(DB_CONNECTION_STRING, "mydb:///potatoes"),
              entry(DB_STATEMENT, "SELECT * FROM potato"),
              entry(DB_OPERATION, "SELECT"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "SELECT * FROM potato"),
              entry(DB_QUERY_SUMMARY, "SELECT potato"),
              entry(DB_OPERATION_NAME, "SELECT"));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_SYSTEM, "myDb"),
              entry(DB_USER, "username"),
              entry(DB_NAME, "potatoes"),
              entry(DB_CONNECTION_STRING, "mydb:///potatoes"),
              entry(DB_STATEMENT, "SELECT * FROM potato"),
              entry(DB_OPERATION, "SELECT"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_SYSTEM_NAME, "myDb"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "SELECT * FROM potato"),
              entry(DB_QUERY_SUMMARY, "SELECT potato"),
              entry(DB_OPERATION_NAME, "SELECT"));
    }

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    AttributesExtractor<Map<String, String>, Void> underTest =
        DbClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), Collections.emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }
}
