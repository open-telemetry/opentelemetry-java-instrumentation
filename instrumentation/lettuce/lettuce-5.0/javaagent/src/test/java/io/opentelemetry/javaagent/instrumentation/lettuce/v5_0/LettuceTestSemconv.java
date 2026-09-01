/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.opentelemetry.api.common.AttributeKey;

@SuppressWarnings({"deprecation", "OtelDeprecatedApiUsage"})
final class LettuceTestSemconv {

  static boolean emitOldDatabaseSemconv() {
    return !Boolean.getBoolean("testStableDatabaseSemconvOnly");
  }

  static boolean emitStableDatabaseSemconv() {
    return Boolean.getBoolean("testStableDatabaseSemconv");
  }

  @SuppressWarnings("unchecked")
  static <T> AttributeKey<T> maybeStable(AttributeKey<T> oldKey) {
    if (!emitStableDatabaseSemconv()) {
      return oldKey;
    }
    if (oldKey.equals(DB_SYSTEM)) {
      return (AttributeKey<T>) DB_SYSTEM_NAME;
    }
    if (oldKey.equals(DB_STATEMENT)) {
      return (AttributeKey<T>) DB_QUERY_TEXT;
    }
    if (oldKey.equals(DB_OPERATION)) {
      return (AttributeKey<T>) DB_OPERATION_NAME;
    }
    return oldKey;
  }

  private LettuceTestSemconv() {}
}
