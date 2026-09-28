/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import org.junit.jupiter.api.Test;

class RedisSpanNameExtractorTest {

  private final Object request = new Object();

  @Test
  void omitsNamespaceFromSpanName() {
    TestGetter getter = new TestGetter();

    assertThat(getter.getDbNamespace(request)).isEqualTo("3");
    assertThat(RedisSpanNameExtractor.create(getter).extract(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "GET localhost:6379" : "GET");
  }

  @Test
  void usesQuerySummaryInStableSemconv() {
    TestGetter getter =
        new TestGetter() {
          @Override
          public String getDbQuerySummary(Object request) {
            return "GET key";
          }
        };

    assertThat(RedisSpanNameExtractor.create(getter).extract(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "GET key" : "GET");
  }

  @Test
  @SuppressWarnings("deprecation") // testing distinct old and stable operation names
  void preservesOldOperationName() {
    TestGetter getter =
        new TestGetter() {
          @Override
          public String getDbOperation(Object request) {
            return "LEGACY GET";
          }
        };

    assertThat(RedisSpanNameExtractor.create(getter).extract(request))
        .isEqualTo(emitStableDatabaseSemconv() ? "GET localhost:6379" : "LEGACY GET");
  }

  private static class TestGetter implements DbClientAttributesGetter<Object, Void> {
    @Override
    public String getDbQueryText(Object request) {
      return null;
    }

    @Override
    public String getDbOperationName(Object request) {
      return "GET";
    }

    @Override
    public String getDbSystemName(Object request) {
      return "redis";
    }

    @Override
    public String getDbNamespace(Object request) {
      return "3";
    }

    @Override
    public String getServerAddress(Object request) {
      return "localhost";
    }

    @Override
    public Integer getServerPort(Object request) {
      return 6379;
    }
  }
}
