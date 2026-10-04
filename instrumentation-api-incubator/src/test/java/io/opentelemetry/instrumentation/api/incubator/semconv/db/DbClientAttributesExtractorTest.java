/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SchemaUrlProvider;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbClientAttributesExtractorTest {

  @Test
  void shouldRetainNetworkPeerWithoutProtocolAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        DbClientAttributesExtractor.create(
            new TestAttributesGetter() {
              @Override
              public String getNetworkPeerAddress(Map<String, String> request, Void response) {
                return "192.0.2.1";
              }

              @Override
              public Integer getNetworkPeerPort(Map<String, String> request, Void response) {
                return 5432;
              }

              @Override
              public String getNetworkTransport(Map<String, String> request, Void response) {
                return "tcp";
              }
            });
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), emptyMap(), null, null);

    assertThat(attributes.build())
        .containsOnly(entry(NETWORK_PEER_ADDRESS, "192.0.2.1"), entry(NETWORK_PEER_PORT, 5432L));
  }

  @Test
  void shouldEmitDatabaseAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("db.system.name", "myDb");
    request.put("db.namespace", "potatoes");
    request.put("db.collection.name", "potato");
    request.put("db.query.text", "SELECT * FROM potato");
    request.put("db.query_summary", "SELECT potato");
    request.put("db.operation.name", "SELECT");

    AttributesExtractor<Map<String, String>, Void> extractor =
        DbClientAttributesExtractor.create(new TestAttributesGetter());
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), request);

    assertThat(attributes.build())
        .containsOnly(
            entry(DB_SYSTEM_NAME, "myDb"),
            entry(DB_NAMESPACE, "potatoes"),
            entry(DB_COLLECTION_NAME, "potato"),
            entry(DB_QUERY_TEXT, "SELECT * FROM potato"),
            entry(DB_QUERY_SUMMARY, "SELECT potato"),
            entry(DB_OPERATION_NAME, "SELECT"));
    assertThat(((SchemaUrlProvider) extractor).internalGetSchemaUrl())
        .isEqualTo(SchemaUrls.V1_44_0);
    assertThat(DbClientSpanNameExtractor.create(new TestAttributesGetter()).extract(request))
        .isEqualTo("SELECT potato");
  }

  @Test
  void shouldProvideSchemaUrl() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        DbClientAttributesExtractor.create(new TestAttributesGetter());

    assertThat(((SchemaUrlProvider) extractor).internalGetSchemaUrl())
        .isEqualTo(SchemaUrls.V1_44_0);
  }

  static class TestAttributesGetter implements DbClientAttributesGetter<Map<String, String>, Void> {
    @Override
    public String getDbSystemName(Map<String, String> map) {
      return map.get("db.system.name");
    }

    @Override
    public String getDbNamespace(Map<String, String> map) {
      return map.get("db.namespace");
    }

    @Override
    public String getDbCollectionName(Map<String, String> map) {
      return map.get("db.collection.name");
    }

    @Override
    public String getDbQueryText(Map<String, String> map) {
      return map.get("db.query.text");
    }

    @Override
    public String getDbQuerySummary(Map<String, String> map) {
      return map.get("db.query_summary");
    }

    @Override
    public String getDbOperationName(Map<String, String> map) {
      return map.get("db.operation.name");
    }
  }

  @Test
  void shouldExtractAllAvailableAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system.name", "myDb");
    request.put("db.namespace", "potatoes");
    request.put("db.collection.name", "potato");
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
    assertThat(startAttributes.build())
        .containsOnly(
            entry(DB_SYSTEM_NAME, "myDb"),
            entry(DB_COLLECTION_NAME, "potato"),
            entry(DB_NAMESPACE, "potatoes"),
            entry(DB_QUERY_TEXT, "SELECT * FROM potato"),
            entry(DB_QUERY_SUMMARY, "SELECT potato"),
            entry(DB_OPERATION_NAME, "SELECT"));
    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    AttributesExtractor<Map<String, String>, Void> underTest =
        DbClientAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldUseExceptionClassWhenErrorTypeIsUnavailable() {
    AttributesExtractor<Map<String, String>, Void> underTest =
        DbClientAttributesExtractor.create(new TestAttributesGetter());
    IllegalStateException error = new IllegalStateException();

    AttributesBuilder attributes = Attributes.builder();
    underTest.onEnd(attributes, Context.root(), emptyMap(), null, error);

    assertThat(attributes.build())
        .containsOnly(entry(ERROR_TYPE, IllegalStateException.class.getName()));
  }
}
