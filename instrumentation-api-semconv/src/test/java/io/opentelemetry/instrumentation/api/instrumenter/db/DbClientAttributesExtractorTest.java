/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbClientAttributesExtractorTest {

  static final class TestAttributesGetter implements DbClientAttributesGetter<Map<String, String>> {
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

    @Override
    public String getConnectionString(Map<String, String> map) {
      return map.get("db.connection_string");
    }

    @Override
    public String getStatement(Map<String, String> map) {
      return map.get("db.statement");
    }

    @Override
    public String getOperation(Map<String, String> map) {
      return map.get("db.operation");
    }
  }

  @Test
  void shouldExtractAllAvailableAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system", "myDb");
    request.put("db.user", "username");
    request.put("db.name", "potatoes");
    request.put("db.connection_string", "mydb:///potatoes");
    request.put("db.statement", "SELECT * FROM potato");
    request.put("db.operation", "SELECT");

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
            entry(SemanticAttributes.DB_SYSTEM, "myDb"),
            entry(SemanticAttributes.DB_USER, "username"),
            entry(SemanticAttributes.DB_NAME, "potatoes"),
            entry(SemanticAttributes.DB_CONNECTION_STRING, "mydb:///potatoes"),
            entry(SemanticAttributes.DB_STATEMENT, "SELECT * FROM potato"),
            entry(SemanticAttributes.DB_OPERATION, "SELECT"));

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
