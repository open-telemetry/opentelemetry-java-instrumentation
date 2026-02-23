/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_PARAMETER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class SqlClientAttributesExtractorTest {

  static class TestAttributesGetter
      implements SqlClientAttributesGetter<Map<String, Object>, Void> {

    @Override
    public SqlDialect getSqlDialect(Map<String, Object> map) {
      return DOUBLE_QUOTES_ARE_STRING_LITERALS;
    }

    @Override
    public Collection<String> getRawQueryTexts(Map<String, Object> map) {
      String queryText = read(map, "db.query.text");
      return queryText == null ? emptySet() : singleton(queryText);
    }

    @Override
    public String getDbSystemName(Map<String, Object> map) {
      return read(map, "db.system");
    }

    @Deprecated
    @Override
    public String getUser(Map<String, Object> map) {
      return read(map, "db.user");
    }

    @Override
    public String getDbNamespace(Map<String, Object> map) {
      return read(map, "db.namespace");
    }

    @Deprecated
    @Override
    public String getConnectionString(Map<String, Object> map) {
      return read(map, "db.connection_string");
    }

    @Override
    public Long getDbOperationBatchSize(Map<String, Object> map) {
      return read(map, DB_OPERATION_BATCH_SIZE.getKey(), Long.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getDbQueryParameters(Map<String, Object> map) {
      Map<String, String> parameters =
          (Map<String, String>) read(map, "db.query.parameter", Map.class);
      return parameters != null ? parameters : emptyMap();
    }

    protected String read(Map<String, Object> map, String key) {
      return read(map, key, String.class);
    }

    protected <T> T read(Map<String, Object> map, String key, Class<T> clazz) {
      return clazz.cast(map.get(key));
    }
  }

  static class TestMultiAttributesGetter extends TestAttributesGetter
      implements SqlClientAttributesGetter<Map<String, Object>, Void> {

    @SuppressWarnings("unchecked")
    @Override
    public Collection<String> getRawQueryTexts(Map<String, Object> map) {
      return (Collection<String>) map.get("db.query.texts");
    }
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void shouldExtractAllAttributes() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.system", "myDb");
    request.put("db.user", "username");
    request.put("db.namespace", "potatoes");
    request.put("db.connection_string", "mydb:///potatoes");
    request.put("db.query.text", "SELECT * FROM potato WHERE id=12345");

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

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
              entry(DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
              entry(DB_OPERATION, "SELECT"),
              entry(DB_SQL_TABLE, "potato"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "SELECT * FROM potato WHERE id=?"),
              entry(DB_QUERY_SUMMARY, "SELECT potato"));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_SYSTEM, "myDb"),
              entry(DB_USER, "username"),
              entry(DB_NAME, "potatoes"),
              entry(DB_CONNECTION_STRING, "mydb:///potatoes"),
              entry(DB_STATEMENT, "SELECT * FROM potato WHERE id=?"),
              entry(DB_OPERATION, "SELECT"),
              entry(DB_SQL_TABLE, "potato"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_SYSTEM_NAME, "myDb"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "SELECT * FROM potato WHERE id=?"),
              entry(DB_QUERY_SUMMARY, "SELECT potato"));
    }

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotExtractTableIfAttributeIsNotSet() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.query.text", "SELECT *");

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, request);

    // then
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DB_STATEMENT, "SELECT *"),
              entry(DB_OPERATION, "SELECT"),
              entry(DB_QUERY_TEXT, "SELECT *"),
              entry(DB_QUERY_SUMMARY, "SELECT"));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(entry(DB_STATEMENT, "SELECT *"), entry(DB_OPERATION, "SELECT"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(entry(DB_QUERY_TEXT, "SELECT *"), entry(DB_QUERY_SUMMARY, "SELECT"));
    }
  }

  @Test
  @SuppressWarnings("deprecation") // to support old database semantic conventions
  void shouldExtractTableToSpecifiedKey() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.query.text", "SELECT * FROM table");

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.<Map<String, Object>, Void>builder(new TestAttributesGetter())
            .setTableAttribute(DB_CASSANDRA_TABLE)
            .build();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, request);

    // then
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DB_STATEMENT, "SELECT * FROM table"),
              entry(DB_OPERATION, "SELECT"),
              entry(DB_CASSANDRA_TABLE, "table"),
              entry(DB_QUERY_TEXT, "SELECT * FROM table"),
              entry(DB_QUERY_SUMMARY, "SELECT table"));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DB_STATEMENT, "SELECT * FROM table"),
              entry(DB_OPERATION, "SELECT"),
              entry(DB_CASSANDRA_TABLE, "table"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(attributes.build())
          .containsOnly(
              entry(DB_QUERY_TEXT, "SELECT * FROM table"), entry(DB_QUERY_SUMMARY, "SELECT table"));
    }
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // when
    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractSingleQueryBatchAttributes() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.namespace", "potatoes");
    request.put("db.query.texts", singleton("INSERT INTO potato VALUES(?)"));
    request.put(DB_OPERATION_BATCH_SIZE.getKey(), 2L);

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestMultiAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAME, "potatoes"),
              entry(DB_STATEMENT, "INSERT INTO potato VALUES(?)"),
              entry(DB_OPERATION, "INSERT"),
              entry(DB_SQL_TABLE, "potato"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "INSERT INTO potato VALUES(?)"),
              entry(DB_QUERY_SUMMARY, "BATCH INSERT potato"),
              entry(DB_OPERATION_BATCH_SIZE, 2L));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAME, "potatoes"),
              entry(DB_STATEMENT, "INSERT INTO potato VALUES(?)"),
              entry(DB_OPERATION, "INSERT"),
              entry(DB_SQL_TABLE, "potato"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "INSERT INTO potato VALUES(?)"),
              entry(DB_QUERY_SUMMARY, "BATCH INSERT potato"),
              entry(DB_OPERATION_BATCH_SIZE, 2L));
    }

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractMultiQueryBatchAttributes() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.namespace", "potatoes");
    request.put(
        "db.query.texts",
        Arrays.asList("INSERT INTO potato VALUES(1)", "INSERT INTO potato VALUES(2)"));
    request.put(DB_OPERATION_BATCH_SIZE.getKey(), 2L);

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestMultiAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAME, "potatoes"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "INSERT INTO potato VALUES(?)"),
              entry(DB_QUERY_SUMMARY, "BATCH INSERT potato"),
              entry(DB_OPERATION_BATCH_SIZE, 2L));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build()).containsOnly(entry(DB_NAME, "potatoes"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "INSERT INTO potato VALUES(?)"),
              entry(DB_QUERY_SUMMARY, "BATCH INSERT potato"),
              entry(DB_OPERATION_BATCH_SIZE, 2L));
    }

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldIgnoreBatchSizeOne() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.namespace", "potatoes");
    request.put("db.query.texts", singleton("INSERT INTO potato VALUES(?)"));
    request.put(DB_OPERATION_BATCH_SIZE.getKey(), 1L);

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.create(new TestMultiAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    if (emitStableDatabaseSemconv() && emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAME, "potatoes"),
              entry(DB_STATEMENT, "INSERT INTO potato VALUES(?)"),
              entry(DB_OPERATION, "INSERT"),
              entry(DB_SQL_TABLE, "potato"),
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "INSERT INTO potato VALUES(?)"),
              entry(DB_QUERY_SUMMARY, "INSERT potato"));
    } else if (emitOldDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAME, "potatoes"),
              entry(DB_STATEMENT, "INSERT INTO potato VALUES(?)"),
              entry(DB_OPERATION, "INSERT"),
              entry(DB_SQL_TABLE, "potato"));
    } else if (emitStableDatabaseSemconv()) {
      assertThat(startAttributes.build())
          .containsOnly(
              entry(DB_NAMESPACE, "potatoes"),
              entry(DB_QUERY_TEXT, "INSERT INTO potato VALUES(?)"),
              entry(DB_QUERY_SUMMARY, "INSERT potato"));
    }

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractQueryParameters() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.namespace", "potatoes");
    // a query with prepared parameters and parameters to sanitize
    request.put(
        "db.query.text",
        "SELECT col FROM table WHERE field1=? AND field2='A' AND field3=? AND field4=2");
    // a prepared parameters map
    Map<String, String> parameterMap = new HashMap<>();
    parameterMap.put("0", "'a'");
    parameterMap.put("1", "1");
    request.put("db.query.parameter", parameterMap);

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.builder(new TestAttributesGetter())
            .setCaptureQueryParameters(true)
            .build();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    String prefix = DB_QUERY_PARAMETER.getAttributeKey("").getKey();
    Attributes queryParameterAttributes =
        startAttributes.removeIf(attribute -> !attribute.getKey().startsWith(prefix)).build();

    // then
    assertThat(queryParameterAttributes)
        .containsOnly(
            entry(DB_QUERY_PARAMETER.getAttributeKey("0"), "'a'"),
            entry(DB_QUERY_PARAMETER.getAttributeKey("1"), "1"));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotExtractQueryParametersForBatch() {
    // given
    Map<String, Object> request = new HashMap<>();
    request.put("db.namespace", "potatoes");
    request.put("db.query.texts", singleton("INSERT INTO potato VALUES(?)"));
    request.put(DB_OPERATION_BATCH_SIZE.getKey(), 2L);
    request.put("db.query.parameter", singletonMap("0", "1"));

    Context context = Context.root();

    AttributesExtractor<Map<String, Object>, Void> underTest =
        SqlClientAttributesExtractor.builder(new TestMultiAttributesGetter())
            .setCaptureQueryParameters(true)
            .build();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    assertThat(startAttributes.build()).doesNotContainKey(DB_QUERY_PARAMETER.getAttributeKey("0"));
    assertThat(endAttributes.build().isEmpty()).isTrue();
  }
}
