/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
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

class DbClientAttributesExtractorTest {

  static final class TestAttributesGetter implements DbClientAttributesGetter<Map<String, String>> {

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

    @Override
    @Nullable
    public String getDbNamespace(Map<String, String> map) {
      return map.get(
          SemconvStabilityUtil.getAttributeKey(AttributeKey.stringKey("db.name")).getKey());
    }

    @Deprecated
    @Override
    public String getConnectionString(Map<String, String> map) {
      return map.get("db.connection_string");
    }

    @Override
    @Nullable
    public String getDbQueryText(Map<String, String> map) {
      return map.get(
          SemconvStabilityUtil.getAttributeKey(AttributeKey.stringKey("db.statement")).getKey());
    }

    @Override
    @Nullable
    public String getDbOperationName(Map<String, String> map) {
      return map.get(
          SemconvStabilityUtil.getAttributeKey(AttributeKey.stringKey("db.operation")).getKey());
    }
  }

  @SuppressWarnings("deprecation") // TODO DbIncubatingAttributes.DB_CONNECTION_STRING deprecation
  @Test
  void shouldExtractAllAvailableAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("db.system", "myDb");
    if (SemconvStability.emitOldDatabaseSemconv()) {
      request.put("db.user", "username");
      request.put("db.connection_string", "mydb:///potatoes");
    }
    request.put(
        SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_NAME).getKey(), "potatoes");
    request.put(
        SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_STATEMENT).getKey(),
        "SELECT * FROM potato");
    request.put(
        SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_OPERATION).getKey(),
        "SELECT");

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
            entry(DbIncubatingAttributes.DB_SYSTEM, "myDb"),
            entry(DbIncubatingAttributes.DB_USER, "username"),
            entry(SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_NAME), "potatoes"),
            entry(DbIncubatingAttributes.DB_CONNECTION_STRING, "mydb:///potatoes"),
            entry(
                SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_STATEMENT),
                "SELECT * FROM potato"),
            entry(
                SemconvStabilityUtil.getAttributeKey(DbIncubatingAttributes.DB_OPERATION),
                "SELECT"));

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
